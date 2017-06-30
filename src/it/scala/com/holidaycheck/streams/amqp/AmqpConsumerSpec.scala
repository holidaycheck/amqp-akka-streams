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

import java.nio.charset.StandardCharsets

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink}
import com.rabbitmq.client.AlreadyClosedException
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}

import scala.collection.immutable.Seq
import scala.concurrent.Future


class AmqpConsumerSpec
  extends AsyncFlatSpec
    with EmbedAmqp
    with BeforeAndAfterAll
    with ScalaFutures
    with Eventually
    with Matchers
    with IntegrationPatience {

  override implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  override protected def beforeAll(): Unit = {
    startAmqp()
  }

  override protected def afterAll(): Unit = {
    stopAmqp()
    system.terminate().futureValue
  }

  private def eventuallyFut[T](body: => T) = Future {
    concurrent.blocking {
      eventually(body)
    }
  }

  "Consumer" should "read all incoming messages in correct order" in {
    // given
    val testCount = 1000
    implicit val intUnmarshaller: AmqpConsumer.PayloadUnmarshaller[Int] = _.decodeString(StandardCharsets.UTF_8).toInt
    val cut = AmqpConsumer(configuration)

    // when
    for (i <- 0 until testCount) publishMessage(i.toString)
    val process: Future[Seq[Delivery[Int]]] = cut.source.alsoTo(cut.sink).runWith(Sink.seq)(materializer)

    // then
    for {
      _ <- eventuallyFut(messageCount should equal(0)) // wait until all messages are consumed
      _ <- cut.shutdown() // close the stream
      result <- process // fetch the result
    } yield {
      val sequence = result.map(_.body)
      sequence should have size testCount
      sequence should be(sorted)
    }
  }

  it should "stop message processing in case of exception, leave failing message at the queue, and be able to recover" in {
    // given
    var failFlag = true
    val failingMessage = "I'm going to fail!"
    val succeedingMessage = "I'm OK!"
    implicit val stringUnmarshaller: AmqpConsumer.PayloadUnmarshaller[String] = _.decodeString(StandardCharsets.UTF_8)
    val cut = AmqpConsumer(configuration)

    // when
    publishMessage(failingMessage)
    publishMessage(succeedingMessage)

    // define graph
    val fail: Flow[Delivery[String], Delivery[String], NotUsed] = Flow.fromFunction { delivery =>
      if (failFlag && delivery.body == failingMessage) throw new RuntimeException(s"$delivery has failed as expected")
      else delivery
    }
    val graph: RunnableGraph[Future[Seq[String]]] =
      cut.source.via(fail).alsoTo(cut.sink).map(_.body).log("outcome").toMat(Sink.seq)(Keep.right)

    // prepare assertions
    def firstRun() = for {
      failed <- graph.run()(materializer).failed
    } yield {
      // first run of the graph fails
      failed shouldBe a[RuntimeException]
      // failing message stayed at the queue, message processing stops at this one
      readMessage(ack = false) shouldBe Some(failingMessage)
    }

    def switchFlag() = Future.successful(failFlag = false)

    def secondRun() = {
      val process = graph.run()(materializer)
      for {
        // wait until there's no message at the queue
        _ <- eventuallyFut(readMessage(ack = false) shouldBe None)
        // let's close the stream...
        _ <- cut.shutdown()
        // second run of the graph succeeds - graph recovers and messages are successfully processed
        result <- process
      } yield result should contain theSameElementsInOrderAs List(failingMessage, succeedingMessage)
    }

    // then run graph with assertions
    for {
      _ <- firstRun()
      _ <- switchFlag()
      outcome <- secondRun()
    } yield outcome
  }

  it should "allow to shutdown the connection" in {
    val cut = AmqpConsumer(configuration)
    for {
      _ <- cut.shutdown()
      exception <- cut.source.runWith(Sink.headOption)(materializer).failed
    } yield exception shouldBe an[AlreadyClosedException]
  }

}
