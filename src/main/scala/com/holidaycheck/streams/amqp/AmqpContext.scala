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
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.rabbitmq.client._

import scala.concurrent.Future
import scala.concurrent.duration._

case class AmqpConfiguration(host: String, port: Int, virtualHost: String, username: String, password: String, queueName: String)

trait AmqpContext {

  /** Returns the current configuration of the amqp connection. */
  def configuration: AmqpConfiguration

  /** Returns the current connection to the amqp server. */
  def connection: Connection

  /** Closes the connection (and all channels). */
  def shutdown(): Future[Unit]

  /** Returns true if the connection to amqp is open. */
  def isOpen: Boolean

}


object AmqpProducer {

  trait PayloadMarshaller[T] {
    def apply(payload: T): ByteString
  }

  implicit object IdentityMarshaller extends PayloadMarshaller[ByteString] {
    override def apply(payload: ByteString): ByteString = identity(payload)
  }

  def apply[T](configuration: AmqpConfiguration)
              (implicit system: ActorSystem, marshaller: AmqpProducer.PayloadMarshaller[T]): AmqpProducer[T] =
    new AmqpProducerImpl(configuration)

}

/**
  * AmqpContext used to send messages to an amqp queue.
  */
trait AmqpProducer[T] extends AmqpContext {

  /**
    * Sends messages to the queue.
    */
  def sink: Sink[T, Future[Done]]

}

object AmqpConsumer {


  trait PayloadUnmarshaller[T] {
    def apply(bytes: ByteString): T
  }

  implicit object IndentityUnmarshaller extends PayloadUnmarshaller[ByteString] {
    override def apply(bytes: ByteString): ByteString = identity(bytes)
  }

  def apply[T](configuration: AmqpConfiguration)
              (implicit system: ActorSystem, unmarshaller: AmqpConsumer.PayloadUnmarshaller[T]): AmqpConsumer[T] =
    new AmqpConsumerImpl(configuration)

}

/**
  * AmqpContext used to consume messages from aqmp queue.
  */
trait AmqpConsumer[T] extends AmqpContext {

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

private[amqp] abstract class BaseAmqpContext(override val configuration: AmqpConfiguration)(implicit system: ActorSystem)
  extends AmqpContext {

  import system.dispatcher

  private val connectionFactory = {
    val timeout = 5.seconds
    val factory = new ConnectionFactory
    factory.setHost(configuration.host)
    factory.setPort(configuration.port)
    factory.setVirtualHost(configuration.virtualHost)
    factory.setUsername(configuration.username)
    factory.setPassword(configuration.password)
    factory.setConnectionTimeout(timeout.toMillis.toInt)
    factory.setAutomaticRecoveryEnabled(true)
    factory.setRequestedHeartbeat(10.seconds.toSeconds.toInt)
    factory.setNetworkRecoveryInterval(10.seconds.toMillis)
    factory
  }

  override lazy val connection: Connection = connectionFactory.newConnection()

  override def shutdown(): Future[Unit] = Future {
    concurrent.blocking {
      connection.close(2.seconds.toMillis.toInt)
    }
  }.recoverWith {
    case _ => Future.successful(())
  }

  override def isOpen: Boolean = connection.isOpen

}


private[amqp] class AmqpProducerImpl[T](configuration: AmqpConfiguration)
                                       (implicit system: ActorSystem, marshaller: AmqpProducer.PayloadMarshaller[T])
  extends BaseAmqpContext(configuration)
    with AmqpProducer[T] {

  override val sink: Sink[T, Future[Done]] =
    Flow.fromFunction[T, ByteString](marshaller.apply)
      .toMat(Sink.foreach { payload =>
        val channel = connection.createChannel()
        try {
          channel.basicPublish("", configuration.queueName, null, payload.toArray)
        } finally {
          channel.close()
        }
      })(Keep.right)

}


private[amqp] class AmqpConsumerImpl[T](configuration: AmqpConfiguration)
                                       (implicit system: ActorSystem, unmarshaller: AmqpConsumer.PayloadUnmarshaller[T])
  extends BaseAmqpContext(configuration)
    with AmqpConsumer[T] {

  private lazy val publisher = new AmqpPublisher(this)

  override lazy val source: Source[Delivery[T], NotUsed] = Source.fromPublisher(publisher)
    .map(delivery => delivery.copy(body = unmarshaller(delivery.body)))

  override lazy val sink: Sink[Delivery[T], Future[Done]] = Sink.foreach(publisher.acker)

}
