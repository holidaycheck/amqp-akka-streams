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

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.util.ByteString
import com.rabbitmq.client.{Channel, Connection, Consumer, Envelope}
import com.rabbitmq.client.AMQP._
import org.reactivestreams.Publisher
import org.reactivestreams.tck.{PublisherVerification, TestEnvironment}
import org.scalamock.scalatest.PathMockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers}
import org.scalatest.testng.TestNGSuiteLike

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration


class AmqpPublisherSpec
  extends PublisherVerification[Delivery[ByteString]](new TestEnvironment(500, true))
    with TestNGSuiteLike
    with Matchers
    with PathMockFactory
    with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem()

  override def afterAll(): Unit = {
    super.afterAll()
    Await.ready(system.terminate(), FiniteDuration(1, TimeUnit.SECONDS))
  }

  def ctx(connectionArg: Connection) = new AmqpContext {
    override val configuration: AmqpConfiguration = AmqpConfiguration(
      host = "host",
      port = 1,
      virtualHost = "vh",
      username = "user",
      password = "password",
      queueName = "queue"
    )
    override val isOpen: Boolean = true
    override val connection: Connection = connectionArg
    override def shutdown(): Future[Unit] = Future.successful(())
  }

  override def createPublisher(elements: Long): Publisher[Delivery[ByteString]] = {
    val consumerTag = "consumer"
    val channel = stub[Channel]
    val connection = stub[Connection]
    (channel.basicQos (_: Int)).when(*).returns(())
    (channel.getChannelNumber _).when().returns(1)
    (channel.abort _).when().returns(())
    (channel.basicConsume (_: String, _: Boolean, _: Consumer)).when(*, *, *).onCall {
      (_, _, consumer) =>
        Future {
          (1 to elements.toInt).foreach { i =>
            consumer.handleDelivery(
              consumerTag,
              new Envelope(i, false, "exchange", "routingKey"),
              new BasicProperties.Builder().build(),
              s"Message $i".getBytes
            )
          }
          consumer.handleCancelOk(consumerTag)
        }
        consumerTag
    }
    (channel.basicCancel _).when(*).returns(())
    (connection.createChannel _).when().returns(channel)

    new AmqpPublisher(ctx(connection))
  }

  override def createFailedPublisher(): Publisher[Delivery[ByteString]] = {
    val channel = stub[Channel]
    val connection = stub[Connection]
    (channel.basicQos (_: Int)).when(*).returns(())
    (channel.getChannelNumber _).when().returns(1)
    (channel.abort _).when().returns(())
    (channel.basicConsume (_: String, _: Boolean, _: Consumer))
      .when(*, *, *)
      .throws(new RuntimeException("Creating consumer fails as expected"))
    (connection.createChannel _).when().returns(channel)

    new AmqpPublisher(ctx(connection))
  }

}
