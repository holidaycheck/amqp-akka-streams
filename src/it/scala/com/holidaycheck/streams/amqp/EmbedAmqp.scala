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

  implicit def system: ActorSystem
  private val amqpExchangeName = "myExchange"
  private val amqpRoutingKey = ""
  private val ctx = new BaseAmqpContext(
    AmqpConfiguration(
      host = "localhost",
      port = 19569,
      virtualHost = "default",
      username = "guest",
      password = "password",
      queueName = "test"
    )
  ){}
  def configuration: AmqpConfiguration = ctx.configuration

  private lazy val broker = EmbeddedAmqpBroker()

  protected def startAmqp(): Unit = {
    broker
    val channel = ctx.connection.createChannel()
    try {
      channel.exchangeDeclare(amqpExchangeName, "fanout", true, false, null)
      channel.queueDeclare(configuration.queueName, true, false, false, null)
      channel.queueBind(configuration.queueName, amqpExchangeName, amqpRoutingKey)
    } finally {
      channel.close()
    }
  }

  protected def publishMessage(msg: String): Unit = {
    val channel = ctx.connection.createChannel()
    try {
      channel.basicPublish(amqpExchangeName, amqpRoutingKey, null, msg.getBytes(StandardCharsets.UTF_8))
    } finally {
      channel.close()
    }
  }

  protected def readMessage(ack: Boolean): Option[String] = {
    val channel = ctx.connection.createChannel()
    try {
      Option(channel.basicGet(configuration.queueName, ack)).map(_.getBody).map(new String(_, StandardCharsets.UTF_8))
    } finally {
      channel.close()
    }
  }

  protected def messageCount: Long = {
    val channel = ctx.connection.createChannel()
    try {
      channel.messageCount(configuration.queueName)
    } finally {
      channel.close()
    }
  }

  protected def stopAmqp(): Unit = {
    ctx.connection.close()
    broker.shutdownBroker()
  }

}
