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

import akka.Done
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.util.ByteString

import scala.concurrent.Future

object AmqpProducer {

  private[amqp] case class Configuration(exchangeName: String, routingKey: String)

  object Configuration {
    def publishToExchange(exchangeName: String, routingKey: String): Configuration =
      Configuration(exchangeName, routingKey)

    def publishToQueue(queueName: String): Configuration =
      Configuration("", queueName)
  }

  trait PayloadMarshaller[T] {
    def apply(payload: T): ByteString
  }

  implicit object IdentityMarshaller extends PayloadMarshaller[ByteString] {
    override def apply(payload: ByteString): ByteString = identity(payload)
  }

  def apply[T](configuration: Configuration)
              (implicit connection: AmqpConnection, marshaller: AmqpProducer.PayloadMarshaller[T]): AmqpProducer[T] =
    new AmqpProducerImpl(configuration)

}

/**
  * Provides [[akka.stream.scaladsl.Sink]] that can be used to send messages to AMQP broker.
  */
trait AmqpProducer[T] {

  /** Sends messages. */
  def sink: Sink[T, Future[Done]]

}

private[amqp] class AmqpProducerImpl[T](configuration: AmqpProducer.Configuration)
                                       (implicit connection: AmqpConnection, marshaller: AmqpProducer.PayloadMarshaller[T])
  extends AmqpProducer[T] {

  override val sink: Sink[T, Future[Done]] =
    Flow.fromFunction[T, ByteString](marshaller.apply)
      .toMat(Sink.foreach { payload =>
        val channel = connection.underlyingConnection.createChannel()
        try {
          channel.basicPublish(configuration.exchangeName, configuration.routingKey, null, payload.toArray)
        } finally {
          channel.close()
        }
      })(Keep.right)

}
