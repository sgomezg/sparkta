/*
 * Copyright (C) 2015 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stratio.sparta.serving.api.service.http

import javax.ws.rs.Path

import akka.pattern.ask
import akka.util.Timeout
import com.stratio.sparta.serving.api.actor.PolicyActor
import com.stratio.sparta.serving.api.actor.PolicyActor.{Delete, FindByFragment, FindByFragmentName, FindByFragmentType, ResponsePolicies}
import com.stratio.sparta.serving.api.constants.HttpConstant
import com.stratio.sparta.serving.core.actor.FragmentActor
import com.stratio.sparta.serving.core.actor.FragmentActor._
import com.stratio.sparta.serving.core.constants.AkkaConstant
import com.stratio.sparta.serving.core.models.{AggregationPoliciesModel, FragmentElementModel}
import com.stratio.spray.oauth2.client.OauthClient
import com.wordnik.swagger.annotations._
import spray.http.{HttpResponse, StatusCodes}
import spray.routing.Route

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

@Api(value = HttpConstant.FragmentPath, description = "Operations over fragments: inputs and outputs that will be " +
  "included in a policy")
trait FragmentHttpService extends BaseHttpService with OauthClient {

  override def routes: Route =
    findAll ~ findByTypeAndId ~ findByTypeAndName ~ findAllByType ~ create ~ update ~ deleteByTypeAndId ~
      deleteByType ~ deleteByTypeAndName ~ deleteAll

  @Path("/{fragmentType}/id/{fragmentId}")
  @ApiOperation(value = "Find a fragment depending of its type and id.",
    notes = "Find a fragment depending of its type and id.",
    httpMethod = "GET",
    response = classOf[FragmentElementModel])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "fragmentType",
      value = "type of fragment (input/output)",
      dataType = "string",
      required = true,
      paramType = "path"),
    new ApiImplicitParam(name = "fragmentId",
      value = "id of the fragment",
      dataType = "string",
      required = true,
      paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = HttpConstant.NotFound,
      message = HttpConstant.NotFoundMessage)
  ))
  def findByTypeAndId: Route = {
    path(HttpConstant.FragmentPath / Segment / "id" / Segment) { (fragmentType, id) =>
      get {
        complete {
          val future = supervisor ? new FindByTypeAndId(fragmentType, id)
          Await.result(future, timeout.duration) match {
            case ResponseFragment(Failure(exception)) => throw exception
            case ResponseFragment(Success(fragment)) => fragment
          }
        }
      }
    }
  }

  @Path("/{fragmentType}/name/{fragmentName}")
  @ApiOperation(value = "Find a fragment depending of its type and name.",
    notes = "Find a fragment depending of its type and name.",
    httpMethod = "GET",
    response = classOf[FragmentElementModel])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "fragmentType",
      value = "type of fragment (input/output)",
      dataType = "string",
      required = true,
      paramType = "path"),
    new ApiImplicitParam(name = "fragmentName",
      value = "name of the fragment",
      dataType = "string",
      required = true,
      paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = HttpConstant.NotFound,
      message = HttpConstant.NotFoundMessage)
  ))
  def findByTypeAndName: Route = {
    path(HttpConstant.FragmentPath / Segment / "name" / Segment) { (fragmentType, name) =>
      get {
        complete {
          val future = supervisor ? new FindByTypeAndName(fragmentType, name)
          Await.result(future, timeout.duration) match {
            case ResponseFragment(Failure(exception)) => throw exception
            case ResponseFragment(Success(fragment)) => fragment
          }
        }
      }
    }
  }

  @Path("/{fragmentType}")
  @ApiOperation(value = "Find a list of fragments depending of its type.",
    notes = "Find a list of fragments depending of its type.",
    httpMethod = "GET",
    response = classOf[FragmentElementModel],
    responseContainer = "List")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "fragmentType",
      value = "type of fragment (input|output)",
      dataType = "string",
      required = true,
      paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = HttpConstant.NotFound,
      message = HttpConstant.NotFoundMessage)
  ))
  def findAllByType: Route = {
    path(HttpConstant.FragmentPath / Segment) { (fragmentType) =>
      get {
        complete {
          val future = supervisor ? new FindByType(fragmentType)
          Await.result(future, timeout.duration) match {
            case ResponseFragments(Failure(exception)) => throw exception
            case ResponseFragments(Success(fragments)) => fragments
          }
        }
      }
    }
  }

  @ApiOperation(value = "Find all fragments",
    notes = "Find all fragments",
    httpMethod = "GET",
    response = classOf[FragmentElementModel],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = HttpConstant.NotFound,
      message = HttpConstant.NotFoundMessage)
  ))
  def findAll: Route = {
    path(HttpConstant.FragmentPath) {
      get {
        complete {
          val future = supervisor ? new FindAllFragments()
          Await.result(future, timeout.duration) match {
            case ResponseFragments(Failure(exception)) => throw exception
            case ResponseFragments(Success(fragments)) => fragments
          }
        }
      }
    }
  }

  @ApiOperation(value = "Creates a fragment depending of its type.",
    notes = "Creates a fragment depending of its type.",
    httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "fragment",
      value = "fragment to save",
      dataType = "FragmentElementModel",
      required = true,
      paramType = "body")
  ))
  def create: Route = {
    path(HttpConstant.FragmentPath) {
      post {
        entity(as[FragmentElementModel]) { fragment =>
          val future = supervisor ? new Create(fragment)
          Await.result(future, timeout.duration) match {
            case ResponseFragment(Failure(exception)) => throw exception
            case ResponseFragment(Success(fragment: FragmentElementModel)) => complete(fragment)
          }
        }
      }
    }
  }

  @ApiOperation(value = "Updates a fragment.",
    notes = "Updates a fragment.",
    httpMethod = "PUT")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "fragment",
      value = "fragment json",
      dataType = "FragmentElementModel",
      required = true,
      paramType = "body")))
  def update: Route = {
    path(HttpConstant.FragmentPath) {
      put {
        entity(as[FragmentElementModel]) { fragment =>
          complete {
            val policyActor = actors.get(AkkaConstant.PolicyActor).get
            val fragmentStatusActor = actors.get(AkkaConstant.FragmentActor).get
            for {
              updateResponse <- fragmentStatusActor ? FragmentActor.Update(fragment)
              allPolicies <- policyActor ? PolicyActor.FindAll()
            } yield (updateResponse, allPolicies) match {
              case (FragmentActor.Response(Success(_)), PolicyActor.ResponsePolicies(Success(policies))) =>
                val policiesInFragments = policies.flatMap(policy => {
                  if (policy.fragments.exists(policyFragment =>
                    policyFragment.fragmentType == fragment.fragmentType &&
                      policyFragment.id == fragment.id))
                    Some(policy)
                  else None
                })
                updatePoliciesWithUpdatedFragments(policiesInFragments)
                HttpResponse(StatusCodes.OK)
              case (Response(Failure(exception)), _) =>
                throw exception
            }
          }
        }
      }
    }
  }

  @Path("/{fragmentType}/id/{fragmentId}")
  @ApiOperation(value = "Deletes a fragment depending of its type and id and their policies related",
    notes = "Deletes a fragment depending of its type and id.",
    httpMethod = "DELETE")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "fragmentType",
      value = "type of fragment (input/output)",
      dataType = "string",
      required = true,
      paramType = "path"),
    new ApiImplicitParam(name = "fragmentId",
      value = "id of the fragment",
      dataType = "string",
      required = true,
      paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = HttpConstant.NotFound, message = HttpConstant.NotFoundMessage)
  ))
  def deleteByTypeAndId: Route = {
    path(HttpConstant.FragmentPath / Segment / "id" / Segment) { (fragmentType, id) =>
      delete {
        complete {
          val policyActor = actors.get(AkkaConstant.PolicyActor).get
          val future = supervisor ? new DeleteByTypeAndId(fragmentType, id)
          Await.result(future, timeout.duration) match {
            case Response(Failure(exception)) =>
              throw exception
            case Response(Success(_)) =>
              Await.result(
                policyActor ? FindByFragment(fragmentType, id), timeout.duration) match {
                case ResponsePolicies(Failure(exception)) => throw exception
                case ResponsePolicies(Success(policies)) =>
                  policies.foreach(policy => policyActor ! Delete(policy.id.get))
              }
              HttpResponse(StatusCodes.OK)
          }
        }
      }
    }
  }

  @Path("/{fragmentType}/name/{fragmentName}")
  @ApiOperation(value = "Deletes a fragment depending of its type and name and their policies related",
    notes = "Deletes a fragment depending of its type and name.",
    httpMethod = "DELETE")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "fragmentType",
      value = "type of fragment (input/output)",
      dataType = "string",
      required = true,
      paramType = "path"),
    new ApiImplicitParam(name = "fragmentName",
      value = "name of the fragment",
      dataType = "string",
      required = true,
      paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = HttpConstant.NotFound, message = HttpConstant.NotFoundMessage)
  ))
  def deleteByTypeAndName: Route = {
    path(HttpConstant.FragmentPath / Segment / "name" / Segment) { (fragmentType, name) =>
      delete {
        complete {
          val policyActor = actors.get(AkkaConstant.PolicyActor).get
          val future = supervisor ? new DeleteByTypeAndName(fragmentType, name)
          Await.result(future, timeout.duration) match {
            case Response(Failure(exception)) =>
              throw exception
            case Response(Success(_)) =>
              Await.result(
                policyActor ? FindByFragmentName(fragmentType, name), timeout.duration) match {
                case ResponsePolicies(Failure(exception)) => throw exception
                case ResponsePolicies(Success(policies)) =>
                  policies.foreach(policy => policyActor ! Delete(policy.id.get))
              }
              HttpResponse(StatusCodes.OK)
          }
        }
      }
    }
  }

  @ApiOperation(value = "Deletes a fragment depending of its type and their policies related",
    notes = "Deletes a fragment depending of its type.",
    httpMethod = "DELETE")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "fragmentType",
      value = "type of fragment (input/output)",
      dataType = "string",
      required = true,
      paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = HttpConstant.NotFound, message = HttpConstant.NotFoundMessage)
  ))
  def deleteByType: Route = {
    path(HttpConstant.FragmentPath / Segment) { (fragmentType) =>
      delete {
        complete {
          val policyActor = actors.get(AkkaConstant.PolicyActor).get
          val future = supervisor ? new DeleteByType(fragmentType)
          Await.result(future, timeout.duration) match {
            case Response(Failure(exception)) =>
              throw exception
            case Response(Success(_)) =>
              Await.result(
                policyActor ? FindByFragmentType(fragmentType), timeout.duration) match {
                case ResponsePolicies(Failure(exception)) => throw exception
                case ResponsePolicies(Success(policies)) =>
                  policies.foreach(policy => policyActor ! Delete(policy.id.get))
              }
              HttpResponse(StatusCodes.OK)
          }
        }
      }
    }
  }

  @ApiOperation(value = "Deletes all fragments and their policies related",
    notes = "Deletes all fragments.",
    httpMethod = "DELETE")
  @ApiResponses(Array(
    new ApiResponse(code = HttpConstant.NotFound, message = HttpConstant.NotFoundMessage)
  ))
  def deleteAll: Route = {
    path(HttpConstant.FragmentPath) {
      delete {
        complete {
          val future = supervisor ? new DeleteAllFragments()
          Await.result(future, timeout.duration) match {
            case Response(Failure(exception)) =>
              throw exception
            case ResponseFragments(Success(fragments: List[FragmentElementModel])) =>
              val fragmentsTypes = fragments.map(fragment => fragment.fragmentType).distinct
              val policyActor = actors.get(AkkaConstant.PolicyActor).get

              fragmentsTypes.foreach(fragmentType =>
                Await.result(
                  policyActor ? FindByFragmentType(fragmentType), timeout.duration) match {
                  case ResponsePolicies(Failure(exception)) =>
                    throw exception
                  case ResponsePolicies(Success(policies)) =>
                    policies.foreach(policy => policyActor ! Delete(policy.id.get))
                })
              HttpResponse(StatusCodes.OK)
          }
        }
      }
    }
  }

  protected def updatePoliciesWithUpdatedFragments(policies: Seq[AggregationPoliciesModel]): Unit =
    policies.foreach(policy => {
      val policyActor = actors.get(AkkaConstant.PolicyActor).get

      policyActor ! PolicyActor.Update(policy)
    })
}