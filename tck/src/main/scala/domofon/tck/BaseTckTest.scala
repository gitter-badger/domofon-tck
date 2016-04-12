package domofon.tck

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpResponse, StatusCodes, MediaTypes}
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.testkit._
import domofon.tck.entities.ContactRequest
import org.scalatest.{FunSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

trait BaseTckTest extends FunSpec with Matchers with ScalatestRouteTest {

  implicit val routeTestTimeout = RouteTestTimeout(new DurationInt(30).second)

  def domofonRoute: Route

  def acceptPlain = addHeader(Accept(MediaTypes.`text/plain`))

  def acceptJson = addHeader(Accept(MediaTypes.`application/json`))

  def contactRequest() = ContactRequest("John Smith", "Company Ltd.", "email@domain.pl", "+48123321123")

  def postContactRequest(cr: ContactRequest = contactRequest()): UUID = {
    import DomofonMarshalling._
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    import spray.json._
    var uuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    val ret = Post("/contacts", cr.toJson) ~> domofonRoute ~> check {
      status shouldBe StatusCodes.OK
      uuid = UUID.fromString(responseAs[String])
    }
    uuid
  }

}