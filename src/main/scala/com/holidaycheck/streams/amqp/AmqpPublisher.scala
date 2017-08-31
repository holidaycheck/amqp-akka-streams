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
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.util.ByteString
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client._
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.collection.JavaConverters._
import scala.collection.immutable.Queue
import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.util.control.NonFatal


private[amqp] class AmqpPublisher(queueName: String)(implicit connection: AmqpConnection)
  extends Publisher[Delivery[ByteString]] {

  private val prefetchCount = 16

  private val channels = new AtomicReference(Map[Int, Channel]())

  override def subscribe(subscriber: Subscriber[_ >: Delivery[ByteString]]): Unit = {
    try {
      val channel = connection.underlyingConnection.createChannel()
      channels.getAndUpdate(new UnaryOperator[Map[Int, Channel]] {
        override def apply(t: Map[Int, Channel]): Map[Int, Channel] = t.updated(channel.getChannelNumber, channel)
      })

      val subscriptionActor = connection.system.actorOf(SubscriptionActor.props)
      val subscription = new AmqpActorSubscription(subscriptionActor, channel)
      val consumer = new AmqpActorConsumer(subscriptionActor, channel)

      subscriptionActor ! SubscriptionActor.Init(subscriber)

      subscriber.onSubscribe(subscription)

      channel.basicQos(prefetchCount)

      channel.basicConsume(queueName, false, consumer)
    } catch {
      case NonFatal(e) =>
        subscriber.onError(e)
    }
  }

  def acker: Delivery[_] => Unit = {
    delivery =>
      channels.get.get(delivery.channelNumber) match {
        case Some(channel) =>
          channel.basicAck(delivery.tag, false)
        case None =>
          throw new IllegalStateException(s"Missing channel for delivery $delivery")
      }
  }
}


private[amqp] object SubscriptionActor {

  case class Init(subscriber: Subscriber[_ >: Delivery[ByteString]])
  case class Request(n: Long)
  case object Deliver
  case class Consume(delivery: Delivery[ByteString])
  case object ConsumerCancelled
  case class Error(error: Throwable)

  def props: Props = Props[SubscriptionActor[_ >: Delivery[ByteString]]]().withDispatcher("amqp-subscription-dispatcher")

}


private[amqp] class AmqpActorSubscription(subscriptionActor: ActorRef, channel: Channel) extends Subscription {

  override def cancel(): Unit = {
    subscriptionActor ! PoisonPill
    Try(channel.abort())
  }

  override def request(n: Long): Unit = subscriptionActor ! SubscriptionActor.Request(n)

}


private[amqp] class SubscriptionActor[T >: Delivery[ByteString]]() extends Actor {

  private var demand = 0L
  private var deliveryQueue = Queue.empty[Delivery[ByteString]]
  private var subscriber: Subscriber[T] = _

  override def receive: Receive = {
    case SubscriptionActor.Init(_subscriber: Subscriber[T]) =>
      subscriber = _subscriber

    case SubscriptionActor.Request(n) if n > 0 =>
      demand = demand + n
      self ! SubscriptionActor.Deliver

    case SubscriptionActor.Request(_) =>
      subscriber.onError(new IllegalArgumentException("3.9 Cannot request for zero or less deliveries"))

    case SubscriptionActor.Deliver if demand > 0 =>
      deliveryQueue.dequeueOption.foreach {
        case (delivery, queue) =>
          deliveryQueue = queue
          demand = demand - 1
          subscriber.onNext(delivery)
          if (demand > 0) {
            self ! SubscriptionActor.Deliver
          }
      }

    case SubscriptionActor.Deliver =>
      // ignore

    case SubscriptionActor.Consume(delivery: Delivery[ByteString]) =>
      deliveryQueue = deliveryQueue.enqueue(delivery)
      self ! SubscriptionActor.Deliver

    case SubscriptionActor.ConsumerCancelled if deliveryQueue.isEmpty =>
      subscriber.onComplete()

    case SubscriptionActor.ConsumerCancelled =>
      context.system.scheduler
        .scheduleOnce(FiniteDuration(15, TimeUnit.MILLISECONDS), self, SubscriptionActor.ConsumerCancelled)(context.system.dispatcher, self)

    case SubscriptionActor.Error(error) =>
      subscriber.onError(error)
  }

}


private[amqp] class AmqpActorConsumer(subscriptionActor: ActorRef, channel: Channel) extends DefaultConsumer(channel) {

  override def handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]): Unit = {
    val delivery = Delivery(
      body = ByteString(body),
      tag = envelope.getDeliveryTag,
      channelNumber = channel.getChannelNumber,
      headers = Option(properties.getHeaders).map(_.asScala.toMap).getOrElse(Map())
    )
    subscriptionActor ! SubscriptionActor.Consume(delivery)
  }

  override def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit = {
    if (!sig.isInitiatedByApplication) {
      subscriptionActor ! SubscriptionActor.Error(sig)
    } else {
      subscriptionActor ! SubscriptionActor.ConsumerCancelled
    }
  }

  override def handleCancel(consumerTag: String): Unit = subscriptionActor ! SubscriptionActor.ConsumerCancelled

  override def handleCancelOk(consumerTag: String): Unit = subscriptionActor ! SubscriptionActor.ConsumerCancelled

}
