/**
 * Copyright (C) 2015 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stratio.sparkta.testat

import java.io.{File, PrintStream}
import java.net._
import java.nio.channels.ServerSocketChannel
import java.nio.file.{Paths, Files}

import akka.event.slf4j.SLF4JLogging
import akka.util.Timeout
import com.stratio.sparkta.serving.api.helpers.SparktaHelper
import com.stratio.sparkta.serving.core.constants.AppConstant
import com.stratio.sparkta.serving.core.helpers.JarsHelper
import com.stratio.sparkta.serving.core.models.{AggregationPoliciesModel, SparktaSerializer}
import com.stratio.sparkta.serving.core.{CuratorFactoryHolder, SparktaConfig}
import com.typesafe.config.ConfigValueFactory
import org.apache.commons.io.FileUtils
import org.apache.curator.test.TestingServer
import org.apache.curator.utils.CloseableUtils
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpecLike}
import spray.client.pipelining._
import spray.http.StatusCodes._
import spray.http._
import spray.testkit.ScalatestRouteTest

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.Source
import scala.util.{Failure, Success, Try}

/**
 * Common operations that will be used in Acceptance Tests. All AT must extends from it.
 */
trait SparktaATSuite
  extends WordSpecLike
  with ScalatestRouteTest
  with SLF4JLogging
  with BeforeAndAfter
  with Matchers
  with SparktaSerializer
  with BeforeAndAfterAll {

  val policyFile: String
  val Localhost = "localhost"
  val SparktaPort = 9090
  val TestServerZKPort = 54646
  val SocketPort = 10666
  val SparktaSleep = 3000
  val PolicySleep = 30000
  val PolicyEndSleep = 30000

  val PathToCsv = getClass.getClassLoader.getResource("fixtures/at-data.csv").getPath
  val CheckpointPath = "checkpoint"
  val LogsPath = "logs"
  val DataPath = "data"

  var zkTestServer: TestingServer = _
  var serverSocket: ServerSocketChannel = _
  var out: PrintStream = _

  /**
   * Starts an embedded ZK server.
   */
  def zookeeperStart: Unit = {
    zkTestServer = new TestingServer()
    zkTestServer.start()
  }

  /**
   * Stop an embedded ZK server.
   */
  def zookeeperStop: Unit = {
    CuratorFactoryHolder.resetInstance()
    CloseableUtils.closeQuietly(zkTestServer)
    zkTestServer.stop()
  }

  /**
   * Starts a socket that will act as an input sending streams of data.
   */
  def socketStart: Unit = {
    serverSocket = ServerSocketChannel.open()
    serverSocket.socket.bind(new InetSocketAddress(Localhost, SocketPort))
  }

  /**
   * Starts an instance of Sparkta with a given configuration (reference.conf in our resources folder).
   */
  def startSparkta: Unit = {

    SparktaConfig.initMainConfig()
    SparktaConfig.initApiConfig()
    SparktaConfig.initSwaggerConfig()

    val clusterZkConfig = Some(SparktaConfig.getZookeeperConfig.get.withValue("connectionString",
      ConfigValueFactory.fromAnyRef(zkTestServer.getConnectString)))
    CuratorFactoryHolder.getInstance(clusterZkConfig)

    SparktaConfig.sparktaHome = getSparktaHome
    JarsHelper.findJarsByPath(
      new File(SparktaConfig.sparktaHome, AppConstant.JarPluginsFolder), Some("-plugin.jar"))

    val sparktaPort = SparktaConfig.apiConfig.get.getInt("port")

    SparktaHelper.initAkkaSystem(AppConstant.ConfigAppName)
    sleep(SparktaSleep)

    openSocket(sparktaPort).isSuccess should be(true)
  }

  /**
   * Opens a socket in a given port
   * @param portNumber of the socket
   * @return a Try object that contains a socket if succeed.
   */
  def openSocket(portNumber: Int): Try[Socket] = {
    Try(new Socket(Localhost, portNumber))
  }

  /**
   * Close a socket
   */
  def closeSocket: Unit = {
    serverSocket.close()
  }

  /**
   * This is a workaround to find the jars either in the IDE or in a maven execution.
   * This test should be moved to acceptance tests when available
   * TODO: this is a unicorn shit and must be changed.
   */
  def getSparktaHome: String = {
    val fileForIde = new File(".", "plugins")

    if (fileForIde.exists()) {
      new File(".").getCanonicalPath
    } else if (new File("../.", "plugins").exists()) {
      new File("../.").getCanonicalPath
    } else {
      new File("../../.").getCanonicalPath
    }
  }

  /**
   * Given a policy it makes an http request to start it on Sparkta.
   * @param path of the policy.
   */
  def sendPolicy(path: String): Unit = {
    val policy = Source.fromFile(new File(path)).mkString // execution context for futures
    val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
    val promise: Future[HttpResponse] =
      pipeline(Post(s"http://${Localhost}:${SparktaPort}/policyContext",
        HttpEntity(ContentType(MediaTypes.`application/json`, HttpCharsets.`UTF-8`), policy)))

    val response: HttpResponse = Await.result(promise, Timeout(5.seconds).duration)

    response.status should be(OK)
    sleep(PolicySleep)
  }

  /**
   * Reads from a CSV file and send data to the socket.
   * @param path of the CSV.
   */
  def sendDataToSparkta(path: String): Unit = {
    out = new PrintStream(serverSocket.socket().accept().getOutputStream())

    Source.fromFile(path).getLines().toList.map(line => {
      log.info(s"> Read data: $line")
      //scalastyle:off
      out.println(line)
      //scalastyle:on
    })

    out.flush()
  }

  protected def sleep(millis: Long): Unit = Thread.sleep(millis)

  def sparktaRunner: Unit = {
    synchronized {
      startSparkta
      sendPolicy(pathToPolicy)
      sendDataToSparkta(PathToCsv)
      sleep(PolicyEndSleep)
      closeSocket
      SparktaHelper.shutdown
    }
  }

  before {
    zookeeperStart
    socketStart
    extraBefore
  }

  def deletePath(path: String): Unit = {
    if (Files.exists(Paths.get(path))) {
      Try(FileUtils.deleteDirectory(new File(path))) match {
        case Success(_) => log.info(s"Path deleted: $path")
        case Failure(e) => log.error(s"Cannot delete: $path", e)
      }
    }
  }

  override def afterAll {
    zookeeperStop
    deletePath(CheckpointPath)
    deletePath(LogsPath)
    deletePath(DataPath)
    extraAfter
  }

  def extraBefore: Unit

  def extraAfter: Unit

  def policy: URL = getClass.getClassLoader.getResource(policyFile)

  def pathToPolicy: String = policy.getPath

  def policyDto: AggregationPoliciesModel = {
    parse(policy.openStream()).extract[AggregationPoliciesModel]
  }
}
