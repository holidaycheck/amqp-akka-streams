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

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}

import scala.concurrent.Future


object AmqpConsumer {

  case class Configuration(queueName: String)

  trait PayloadUnmarshaller[T] {
    def apply(bytes: ByteString): T
  }

  implicit object IndentityUnmarshaller extends PayloadUnmarshaller[ByteString] {
    override def apply(bytes: ByteString): ByteString = identity(bytes)
  }

  def apply[T](configuration: Configuration)
              (implicit connection: AmqpConnection, unmarshaller: AmqpConsumer.PayloadUnmarshaller[T]): AmqpConsumer[T] =
    new AmqpConsumerImpl(configuration)

}

/**
  * Provides [[akka.stream.scaladsl.Source]] for reading messages from AMQP queue and [[akka.stream.scaladsl.Sink]] for
  * acking them.
  */
trait AmqpConsumer[T] {

  /**
    * Consumes AMQP messages from a queue and publishes [[Delivery]] to the stream.
    */
  def source: Source[Delivery[T], NotUsed]

  /**
    * Acks [[Delivery]] at the end of its processing in the stream. Delivery must
    * be acked using the same channel from which it was emitted. If the delivery won't
    * be acked then the corresponding message will stay at the queue.
    */
  def sink: Sink[Delivery[T], Future[Done]]

}

private[amqp] class AmqpConsumerImpl[T](configuration: AmqpConsumer.Configuration)
                                       (implicit connection: AmqpConnection, unmarshaller: AmqpConsumer.PayloadUnmarshaller[T])
  extends AmqpConsumer[T] {

  private lazy val publisher = new AmqpPublisher(configuration.queueName)

  override lazy val source: Source[Delivery[T], NotUsed] = Source.fromPublisher(publisher)
    .map(delivery => delivery.copy(body = unmarshaller(delivery.body)))

  override lazy val sink: Sink[Delivery[T], Future[Done]] = Sink.foreach(publisher.acker)

}
