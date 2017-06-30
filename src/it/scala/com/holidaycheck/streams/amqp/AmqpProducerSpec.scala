/**
  * The MIT License (MIT)
  * Copyright © 2017 HolidayCheck AG
  *
  * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
  * documentation files (the “Software”), to deal in the Software without restriction, including without limitation the
  * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
  * permit persons to whom the Software is furnished to do so, subject to the following conditions:
  *
  * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
  * Software.
  *
  * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
  * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS
  * OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
  * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
  */

package com.holidaycheck.streams.amqp

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.rabbitmq.client.AlreadyClosedException
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}


class AmqpProducerSpec
  extends AsyncFlatSpec
    with EmbedAmqp
    with BeforeAndAfterAll
    with ScalaFutures
    with Matchers
    with IntegrationPatience {

  override implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val stringMarshaller: AmqpProducer.PayloadMarshaller[String] = ByteString.apply(_)

  val cut = AmqpProducer(configuration)
  val testMessage = "test message"

  override protected def beforeAll(): Unit = {
    startAmqp()
  }

  override protected def afterAll(): Unit = {
    stopAmqp()
    system.terminate().futureValue
  }

  "Producer" should "send a message" in {
    Source.single(testMessage).runWith(cut.sink).map { _ =>
      readMessage(true) shouldBe Some(testMessage)
    }
  }

  it should "allow to shutdown the connection" in {
    cut.shutdown()
    recoverToSucceededIf[AlreadyClosedException] {
      Source.single(testMessage).runWith(cut.sink)
    }
  }

}
