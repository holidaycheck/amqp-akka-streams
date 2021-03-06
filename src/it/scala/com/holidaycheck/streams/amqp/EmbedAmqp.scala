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

import akka.actor.ActorSystem


trait EmbedAmqp {

  protected implicit def system: ActorSystem
  protected val amqpExchangeName = "myExchange"
  protected val amqpRoutingKey = ""
  protected val amqpQueueName = "myQueue"
  protected val connectionConfiguration: AmqpConnection.Configuration = AmqpConnection.Configuration(
    host = "localhost",
    port = 19569,
    virtualHost = "default",
    username = "guest",
    password = "password"
  )
  private val testConnection: AmqpConnection = AmqpConnection(connectionConfiguration)

  private lazy val broker = EmbeddedAmqpBroker()

  protected def startAmqp(): Unit = {
    broker
    val channel = testConnection.underlyingConnection.createChannel()
    try {
      channel.exchangeDeclare(amqpExchangeName, "fanout", true, false, null)
      channel.queueDeclare(amqpQueueName, true, false, false, null)
      channel.queueBind(amqpQueueName, amqpExchangeName, amqpRoutingKey)
    } finally {
      channel.close()
    }
  }

  protected def publishMessage(msg: String): Unit = {
    val channel = testConnection.underlyingConnection.createChannel()
    try {
      channel.basicPublish(amqpExchangeName, amqpRoutingKey, null, msg.getBytes(StandardCharsets.UTF_8))
    } finally {
      channel.close()
    }
  }

  protected def readMessage(ack: Boolean): Option[String] = {
    val channel = testConnection.underlyingConnection.createChannel()
    try {
      Option(channel.basicGet(amqpQueueName, ack)).map(_.getBody).map(new String(_, StandardCharsets.UTF_8))
    } finally {
      channel.close()
    }
  }

  protected def messageCount: Long = {
    val channel = testConnection.underlyingConnection.createChannel()
    try {
      channel.messageCount(amqpQueueName)
    } finally {
      channel.close()
    }
  }

  protected def stopAmqp(): Unit = {
    testConnection.underlyingConnection.close()
    broker.shutdownBroker()
  }

}
