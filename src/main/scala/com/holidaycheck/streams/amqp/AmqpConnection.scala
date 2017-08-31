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
import com.rabbitmq.client.{Connection, ConnectionFactory}

import scala.concurrent.Future
import scala.concurrent.duration._

object AmqpConnection {
  /** Defines connection to the broker. */
  case class Configuration(
                            host: String,
                            port: Int,
                            virtualHost: String,
                            username: String,
                            password: String
                          )

  def apply(configuration: Configuration)(implicit system: ActorSystem): AmqpConnection =
    new AmqpConnectionImpl(configuration)
}

/**
  * Holds connection to the broker.
  */
trait AmqpConnection {

  /** Wrapped actor system */
  private[amqp] def system: ActorSystem

  /** Wrapped connection instance */
  private[amqp] def underlyingConnection: Connection

  /** Closes the connection (and all channels). */
  def shutdown(): Future[Unit]

  /** Returns true if the connection to broker is open. */
  def isOpen: Boolean

}


private[amqp] class AmqpConnectionImpl(configuration: AmqpConnection.Configuration)
                                      (override implicit val system: ActorSystem)
  extends AmqpConnection {

  import system.dispatcher

  private val connectionFactory = {
    val factory = new ConnectionFactory
    factory.setHost(configuration.host)
    factory.setPort(configuration.port)
    factory.setVirtualHost(configuration.virtualHost)
    factory.setUsername(configuration.username)
    factory.setPassword(configuration.password)
    factory.setConnectionTimeout(5.seconds.toMillis.toInt)
    factory.setAutomaticRecoveryEnabled(true)
    factory.setRequestedHeartbeat(10.seconds.toSeconds.toInt)
    factory.setNetworkRecoveryInterval(10.seconds.toMillis)
    factory
  }

  override lazy val underlyingConnection: Connection = connectionFactory.newConnection()

  override def shutdown(): Future[Unit] = Future {
    concurrent.blocking {
      underlyingConnection.close(2.seconds.toMillis.toInt)
    }
  }.recoverWith {
    case _ => Future.successful(())
  }

  override def isOpen: Boolean = underlyingConnection.isOpen

}
