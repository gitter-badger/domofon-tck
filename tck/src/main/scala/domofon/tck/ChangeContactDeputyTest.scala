package domofon.tck

import java.util.UUID

import domofon.tck.DomofonMarshalling._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import domofon.tck.entities.{EntityCreatedWithSecret, Deputy}
import spray.json._

trait ChangeContactDeputyTest extends BaseTckTest {

  private[this] def deputyUrl(contactId: UUID): String = {
    s"/contacts/${contactId}/deputy"
  }

  private[this] def contactDeputy() = Deputy(
    "Jan Kowalski", "jan.kowalski@company.pl", "+48123321123"
  )

  private[this] def putContactDeputy(contactId: UUID, secret: UUID, deputy: Deputy = contactDeputy()) = {
    Put(deputyUrl(contactId), deputy.toJson) ~> acceptJson ~> authorizeWithSecret(secret) ~> domofonRoute
  }

  describe("GET /contacts/{id}/deputy") {

    it("When contact doesn't exist it also returns 404") {
      Get(deputyUrl(nonExistentUuid)) ~> acceptJson ~~> {
        status shouldBe StatusCodes.NotFound
      }
    }

    it("By default Contact has no deputy") {
      val uuid: UUID = postContactRequest().id

      Get(deputyUrl(uuid)) ~> acceptJson ~~> {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  describe("PUT /contacts/{id}/deputy") {

    it("When contact doesn't exist it is impossible to create deputy") {
      putContactDeputy(nonExistentUuid, nonExistentUuid) ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    it("It is possible to create deputy with PUT /contacts/{id}/deputy") {
      val EntityCreatedWithSecret(uuid, secret) = postContactRequest()

      putContactDeputy(uuid, secret) ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    it("Can respond only with application/json and fails with other response types on GET /contacts/{id}/deputy when exists") {
      val EntityCreatedWithSecret(uuid, secret) = postContactRequest()

      putContactDeputy(uuid, secret) ~> check {
        status shouldBe StatusCodes.OK
      }

      Get(deputyUrl(uuid)) ~> acceptPlain ~~> {
        status shouldBe StatusCodes.NotAcceptable
      }
    }

    it("When deputy was created it could be retrieved GET /contacts/{id}/deputy") {
      val EntityCreatedWithSecret(uuid, secret) = postContactRequest()
      val deputy = contactDeputy()

      putContactDeputy(uuid, secret, deputy) ~> check {
        status shouldBe StatusCodes.OK
      }

      Get(deputyUrl(uuid)) ~> acceptJson ~~> {
        status shouldBe StatusCodes.OK
        responseAs[Deputy] shouldBe deputy
      }
    }

    it("Deputy could be overwritten") {
      val EntityCreatedWithSecret(uuid, secret) = postContactRequest()
      val deputy = contactDeputy()
      val deputy2 = deputy.copy(name = "Other Person")

      putContactDeputy(uuid, secret, deputy) ~> check {
        status shouldBe StatusCodes.OK
      }

      Get(deputyUrl(uuid)) ~> acceptJson ~~> {
        status shouldBe StatusCodes.OK
        responseAs[Deputy] shouldBe deputy
      }

      putContactDeputy(uuid, secret, deputy2) ~> check {
        status shouldBe StatusCodes.OK
      }

      Get(deputyUrl(uuid)) ~> acceptJson ~~> {
        status shouldBe StatusCodes.OK
        responseAs[Deputy] shouldBe deputy2
      }
    }
  }

  describe("DELETE /contacts/{id}/deputy") {

    it("When contact doesn't exist it is impossible to delete deputy") {
      Delete(deputyUrl(nonExistentUuid)) ~> authorizeWithSecret(nonExistentUuid) ~~> {
        status shouldBe StatusCodes.NotFound
      }
    }

    it("Allows deleting even if there is no deputy set ") {
      val EntityCreatedWithSecret(uuid, secret) = postContactRequest()
      Delete(deputyUrl(uuid)) ~> authorizeWithSecret(secret) ~~> {
        status shouldBe StatusCodes.OK
      }
    }

    it("When deputy was added, it could be removed") {
      val EntityCreatedWithSecret(uuid, secret) = postContactRequest()
      val deputy = contactDeputy()

      putContactDeputy(uuid, secret, deputy) ~> check {
        status shouldBe StatusCodes.OK
      }

      Get(deputyUrl(uuid)) ~> acceptJson ~~> {
        status shouldBe StatusCodes.OK
        responseAs[Deputy] shouldBe deputy
      }

      Delete(deputyUrl(uuid)) ~> authorizeWithSecret(secret) ~~> {
        status shouldBe StatusCodes.OK
      }

      Get(deputyUrl(uuid)) ~> acceptJson ~~> {
        status shouldBe StatusCodes.NotFound
      }

    }

  }

}
