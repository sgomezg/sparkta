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

package com.stratio.sparkta.testat.outputs


import com.redis.RedisClientPool
import com.stratio.sparkta.testat.SparktaATSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import redis.embedded.RedisServer

/**
  * Acceptance test:
  * [Input]: Socket.
  * [Output]: Redis.
  * [Operators]: accumulator, avg, count, firsValue, fullText, lastValue, max,
  * median, min, range, stddev, sum, variance.
  */

@RunWith(classOf[JUnitRunner])
class ISocketORedisOperatorsIT extends SparktaATSuite {


  override val PathToCsv = getClass.getClassLoader.getResource("fixtures/at-data-operators.csv").getPath
  override val policyFile = "policies/ISocket-ORedis-operators.json"

  val TestRedisHost = "localhost"
  val TestRedisPort = 63790

  var redisPool: RedisClientPool = _
  var redisServer: RedisServer = _

  val NumEventsExpected = 2

  "Sparkta" should {
    "starts and executes a policy that reads from a socket and writes in redis" in {
      sparktaRunner
      checkData
    }

    def checkData(): Unit = {

      val productSize = redisPool.withClient(client =>
        client.keys("*")
      )
      productSize.get.size should be(NumEventsExpected)

      val productAKey = redisPool.withClient(client =>
        client.keys("product:producta:minute:*")
      ).get.head.get

      val productA = redisPool.withClient(client =>
        client.hgetall(productAKey)
      )
      productA.get.get("first_price").get should be("10")
      productA.get.get("last_price").get should be("600")
      productA.get.get("max_price").get should be("1002.0")
      productA.get.get("sum_price").get should be("5112.0")
      productA.get.get("count_price").get should be("8")
      productA.get.get("min_price").get should be("10.0")

      val productBKey = redisPool.withClient(client => client.keys("product:productb:minute:*")).get.head.get
      val productB = redisPool.withClient(client => client.hgetall(productBKey))
      productB.get.get("first_price").get should be("15")
      productB.get.get("last_price").get should be("50")
      productB.get.get("max_price").get should be("1001.0")
      productB.get.get("sum_price").get should be("6066.0")
      productB.get.get("count_price").get should be("8")
      productB.get.get("min_price").get should be("15.0")
    }
  }

  override def extraBefore: Unit = {
    redisServer = new RedisServer(TestRedisPort)
    redisServer.start()
    redisPool = new RedisClientPool(TestRedisHost, TestRedisPort)
  }

  override def extraAfter: Unit = redisServer.stop()

}
