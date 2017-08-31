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
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}

import scala.concurrent.Future


class AmqpConnectionITSpec
  extends AsyncFlatSpec
    with EmbedAmqp
    with BeforeAndAfterAll
    with ScalaFutures
    with Matchers
    with IntegrationPatience {

  override implicit val system: ActorSystem = ActorSystem()

  override protected def beforeAll(): Unit = {
    startAmqp()
  }

  override protected def afterAll(): Unit = {
    stopAmqp()
    system.terminate().futureValue
  }

  "Connection" should "be able to connect and disconnect from the broker" in {
    val cut = AmqpConnection(connectionConfiguration)
    for {
      open <- Future.successful(cut.isOpen)
      if open
      _ <- cut.shutdown()
    } yield cut.isOpen should be(false)

  }

}
