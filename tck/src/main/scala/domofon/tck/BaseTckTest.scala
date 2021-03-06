package domofon.tck

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.testkit._
import domofon.tck.entities.{EntityCreated, EntityCreatedWithSecret, PostCategory, PostContact}
import org.scalatest.{FunSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Random, Try}
import spray.json._

trait BaseTckTest extends FunSpec with Matchers with ScalatestRouteTest with AdminCredentials {

  implicit val routeTestTimeout: RouteTestTimeout = RouteTestTimeout(new DurationInt(30).second)

  val nonExistentUuid: UUID = UUID.fromString("00000000-0000-0000-0000-420000000000")

  def domofonRoute: Route

  def acceptPlain: HttpRequest => HttpRequest = addHeader(Accept(MediaTypes.`text/plain`))

  def acceptJson: HttpRequest => HttpRequest = addHeader(Accept(MediaTypes.`application/json`))

  def randomString(length: Int = 10): String = Random.alphanumeric.take(length).mkString

  def randomSentence(words: Int = 5) = List.fill(words)(randomString(2 + Random.nextInt(5))).mkString(" ")

  def randomEmail = s"${List.fill(2)(randomString(2 + Random.nextInt(5))).mkString(".")}@${randomSentence(1)}.pl".toUpperCase

  def contactRequest(
    name: String = randomSentence(2), category: UUID = postCategoryRequest().id
  ): PostContact = {
    PostContact(name, category, randomEmail)
  }

  def categoryRequest(
    name: String = randomString(),
    description: String = randomSentence(),
    message: String = "Notification message",
    isIndividual: Boolean = false
  ): PostCategory = {
    PostCategory(name, description, message, isIndividual)
  }

  def maxSampleLength: Int = 1000

  def postContactRequest(cr: PostContact = contactRequest()): EntityCreatedWithSecret = {
    import DomofonMarshalling._
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    import spray.json._
    var result = EntityCreatedWithSecret(nonExistentUuid, nonExistentUuid)
    val ret = Post("/contacts", cr.toJson) ~> acceptJson ~~> {
      status shouldBe StatusCodes.OK
      result = responseAs[EntityCreatedWithSecret]
    }
    result
  }

  def postCategoryRequest(cr: PostCategory = categoryRequest(), adminToken: AdminToken = loginAdmin): EntityCreated = {
    import DomofonMarshalling._
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    import spray.json._
    var result = EntityCreated(nonExistentUuid)
    val ret = Post("/categories", cr.toJson) ~> authorizeWithSecret(adminToken) ~> acceptJson ~~> {
      status shouldBe StatusCodes.OK
      result = responseAs[EntityCreated]
    }
    result
  }

  def formatedEntityString(entity: HttpEntity): String = {
    val content = Await.result(entity.dataBytes.runReduce(_ ++ _), Duration.Inf).utf8String
    Try(content.parseJson.prettyPrint).getOrElse {
      val len = content.length
      if (len + 3 > maxSampleLength) {
        content.take(maxSampleLength) + "..."
      } else {
        content
      }
    }
  }

  private[this] def contentTypeStringOrEmpty(entity: HttpEntity): String = {
    if (entity.contentType == ContentTypes.NoContentType) ""
    else
      s"Content-type: ${entity.contentType}\n"
  }

  private[this] def headersStringOrEmpty(headers: Seq[HttpHeader]): String = {
    headers.mkString("", "\n", "\n")
  }

  def authorizeWithSecret(secret: UUID): RequestTransformer = {
    authorizeWithSecret(secret.toString)
  }

  def authorizeWithSecret(secret: String): RequestTransformer = {
    addHeader(Authorization(OAuth2BearerToken(secret)))
  }

  def requestAsString(request: HttpRequest): String = {
    val strBuilder = StringBuilder.newBuilder
    strBuilder
      .append("\n")
      .append(request.method.value).append(" ").append(request.uri).append("\n")
      .append(headersStringOrEmpty(request.headers))
      .append(contentTypeStringOrEmpty(request.entity))
      .append(formatedEntityString(request.entity))
      .toString()
  }

  def responseAsString(response: HttpResponse): String = {
    val strBuilder = StringBuilder.newBuilder
    strBuilder
      .append("\n")
      .append(response.status.value).append("\n")
      .append(headersStringOrEmpty(response.headers))
      .append(contentTypeStringOrEmpty(response.entity))
      .append(formatedEntityString(response.entity))
      .toString()
  }

  implicit class PimpedRequest(request: HttpRequest) {
    /**
     * Shortcut to run request using domofonRoute
     *
     * Prints nicely Request and Response in case of error
     */
    def ~~>[T](body: => T) = request ~> domofonRoute ~> check {
      try {
        val a = body
        a
      } catch {
        case t: Throwable =>
          info("  Request was:")
          info("/--------------------------------------\\")
          info(requestAsString(request))
          info("\\--------------------------------------/")
          info("  Response was:")
          info("/--------------------------------------\\")
          info(responseAsString(response))
          info("\\--------------------------------------/")
          throw t
      }
    }
  }

}

