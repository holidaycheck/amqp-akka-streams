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


class AmqpConsumerITSpec
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
  val consumerConfiguration = AmqpConsumer.Configuration(amqpQueueName)

  case class ExpectedException(delivery: Delivery[String]) extends RuntimeException(s"$delivery has failed as expected")

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
    implicit val connection: AmqpConnection = AmqpConnection(connectionConfiguration)
    val cut = AmqpConsumer(consumerConfiguration)

    // when
    for (i <- 0 until testCount) publishMessage(i.toString)
    val process: Future[Seq[Delivery[Int]]] = cut.source.alsoTo(cut.sink).runWith(Sink.seq)

    // then
    for {
      _ <- eventuallyFut(messageCount should equal(0)) // wait until all messages are consumed
      _ <- connection.shutdown() // close the stream
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
    implicit val connection: AmqpConnection = AmqpConnection(connectionConfiguration)
    val cut = AmqpConsumer(consumerConfiguration)

    // when
    publishMessage(failingMessage)
    publishMessage(succeedingMessage)

    // define graph
    val fail: Flow[Delivery[String], Delivery[String], NotUsed] = Flow.fromFunction { delivery =>
      if (failFlag && delivery.body == failingMessage) throw ExpectedException(delivery)
      else delivery
    }
    val graph: RunnableGraph[Future[Seq[String]]] =
      cut.source.via(fail).alsoTo(cut.sink).map(_.body).log("outcome").toMat(Sink.seq)(Keep.right)

    // prepare assertions
    def firstRun() = for {
      // first run of the graph fails
      _ <- recoverToExceptionIf[ExpectedException](graph.run())
    } yield {
      // failing message stayed at the queue, message processing stops at this one
      readMessage(ack = false) shouldBe Some(failingMessage)
    }

    def switchFlag() = Future.successful(failFlag = false)

    def secondRun() = {
      val process = graph.run()
      for {
        // wait until there's no message at the queue
        _ <- eventuallyFut(readMessage(ack = false) shouldBe None)
        // let's close the stream...
        _ <- connection.shutdown()
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

  it should "fail to read if the connection is shutdown" in {
    implicit val connection: AmqpConnection = AmqpConnection(connectionConfiguration)
    val cut = AmqpConsumer(consumerConfiguration)
    for {
      _ <- connection.shutdown()
      assertion <- recoverToSucceededIf[AlreadyClosedException](cut.source.runWith(Sink.headOption))
    } yield assertion
  }

}
