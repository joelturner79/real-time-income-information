/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import connectors.DesConnector
import models.RequestDetails
import models.response.{DesFailureResponse, DesSuccessResponse}
import org.scalatest.MustMatchers
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import services.RealTimeIncomeInformationService
import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.domain.Nino
import utils.WireMockHelper

import scala.concurrent.Future

class RealTimeIncomeInformationServiceSpec extends PlaySpec with MustMatchers with BaseSpec with MockitoSugar with ScalaFutures {

  "RealTimeIncomeInformationService" when {

    def service(desConnector: DesConnector) = new RealTimeIncomeInformationService(desConnector)

    val taxYear = Json.parse("""
                                     |    {
                                     |      "taxYear": "16-17",
                                     |      "taxYearIndicator": "P",
                                     |      "hmrcOfficeNumber": "099",
                                     |      "employerPayeRef": "A1B2c3d4e5",
                                     |      "employerName1": "Employer",
                                     |      "nationalInsuranceNumber": "AB123456C",
                                     |      "surname": "Surname",
                                     |      "gender": "M",
                                     |      "uniqueEmploymentSequenceNumber": 9999,
                                     |      "taxablePayInPeriod": 999999.99,
                                     |      "taxDeductedOrRefunded": -12345.67,
                                     |      "grossEarningsForNICs": 888888.66,
                                     |      "taxablePayToDate": 999999.99,
                                     |      "totalTaxToDate": 654321.08,
                                     |      "numberOfNormalHoursWorked": "E",
                                     |      "payFrequency": "M1",
                                     |      "paymentDate": "2017-02-03",
                                     |      "earningsPeriodsCovered": 11,
                                     |      "uniquePaymentId": 777777,
                                     |      "paymentConfidenceStatus": "1",
                                     |      "taxCode": "11100L",
                                     |      "hmrcReceiptTimestamp": "2018-04-16T09:23:55Z",
                                     |      "rtiReceivedDate": "2018-04-16",
                                     |      "apiAvailableTimestamp": "2018-04-16T09:23:55Z"
                                     |    }
                                     """.stripMargin)

    val desResponseWithOneTaxYear = DesSuccessResponse(63, List(taxYear))
    val desResponseWithTwoTaxYears = DesSuccessResponse(63, List(taxYear,taxYear))

    "pickOneValue is called" must {

      "return the corresponding value if the requested key is present in the given DesSuccessResponse object" in {
        val result = service(mock[DesConnector]).pickOneValue("surname", taxYear)
        result mustBe "surname" -> JsString("Surname")
      }

      "return a value of 'undefined' if the requested key is not present in the given DesSuccessResponse object" in {
        val result = service(mock[DesConnector]).pickOneValue("test", taxYear)
        result mustBe "test" -> JsString("undefined")
      }

    }

    "pickAll is called" when {

      "when a single tax year is requested" must {

        "return all requested values when all keys are present" in {
          val result = service(mock[DesConnector]).pickAll(List("surname", "nationalInsuranceNumber"), desResponseWithOneTaxYear)

          result mustBe Json.parse(
            """
              |{
              |"taxYears" : [ {
              |"surname": "Surname",
              |"nationalInsuranceNumber":"AB123456C"
              |}
              |]
              |}
            """.stripMargin)
        }

        "return all requested values plus an 'undefined' when all keys except one are present" in {
          val result = service(mock[DesConnector]).pickAll(List("surname", "nationalInsuranceNumber", "test"), desResponseWithOneTaxYear)

          result mustBe Json.parse(
            """
              |{ "taxYears" : [ {
              |"surname": "Surname",
              |"nationalInsuranceNumber":"AB123456C",
              |"test":"undefined"
              |}
              |]
              |}
            """.stripMargin)
        }

      }

      "when multiple tax years are requested" must {

        "return all requested values when all keys are present and the data covers multiple years" in {

          val expectedJson = Json.parse(
            """
              |{
              |"taxYears" : [
              |{
              |"surname": "Surname",
              |"nationalInsuranceNumber":"AB123456C"
              |},
              |{
              |"surname": "Surname",
              |"nationalInsuranceNumber":"AB123456C"
              |}
              |]
              |}
            """.stripMargin)

          val result = service(mock[DesConnector]).pickAll(List("surname", "nationalInsuranceNumber"), desResponseWithTwoTaxYears)

          result mustBe expectedJson
        }

      }

    }
        "retrieve citizen income is called" when{

          "given a DES success response" must {

            "retrieve and filter data" in {

              val matchingDetails = RequestDetails("2016-12-31", "2017-12-31", "Smith", None, None, None, None, None, List("surname", "nationalInsuranceNumber"))
              val mockDesConnector = mock[DesConnector]

              when(mockDesConnector.retrieveCitizenIncome(any(), any())(any())).thenReturn(Future.successful(DesSuccessResponse(1, List(taxYear))))

              val expectedJson = Json.parse(
                """
                  |{
                  |"taxYears" : [
                  |{
                  |"surname": "Surname",
                  |"nationalInsuranceNumber":"AB123456C"
                  |}
                  |]
                  |}
                """.stripMargin)

              whenReady(service(mockDesConnector).retrieveCitizenIncome(Nino("AB123456C"), matchingDetails)) {
                result => result mustBe expectedJson
              }
            }
          }

          "given a DES failure response return an appropriate error message" in {

            val matchingDetails = RequestDetails("2016-12-31", "2017-12-31", "Smith", None, None, None, None, None, List("surname", "nationalInsuranceNumber"))
            val mockDesConnector = mock[DesConnector]

            when(mockDesConnector.retrieveCitizenIncome(any(), any())(any())).thenReturn(Future.successful(DesFailureResponse("INVALID_NINO", "Submission has not passed validation. Invalid parameter nino.")))

            whenReady(service(mockDesConnector).retrieveCitizenIncome(Nino("AB123456C"), matchingDetails)) {
              result => result mustBe invalidNinoJson
            }
          }
        }
  }

}
