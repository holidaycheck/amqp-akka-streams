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

import java.io.File
import java.nio.charset.StandardCharsets

import com.google.common.io.Files
import org.apache.qpid.server.{Broker, BrokerOptions}

import scala.io.Source


object EmbeddedAmqpBroker {

  val DEFAULT_AMQP_HOST = "localhost"
  val DEFAULT_AMQP_PORT = 19569
  val DEFAULT_HTTP_PORT = 9568
  val DEFAULT_LOGIN = "guest"
  val DEFAULT_PASSWORD = "password"

  def apply(
             host: String = EmbeddedAmqpBroker.DEFAULT_AMQP_HOST,
             amqpPort: Int = EmbeddedAmqpBroker.DEFAULT_AMQP_PORT,
             httpPort: Int = EmbeddedAmqpBroker.DEFAULT_HTTP_PORT,
             login: String = EmbeddedAmqpBroker.DEFAULT_LOGIN,
             password: String = EmbeddedAmqpBroker.DEFAULT_PASSWORD
             ): EmbeddedAmqpBroker = {

    val tmpFolder = Files.createTempDir()
    val configFileName = "config.json"
    val broker = new Broker()
    val brokerOptions = new BrokerOptions()

    val homeDir = makeHomeDir(tmpFolder)
    val workDir = new File(homeDir, "work")

    System.setProperty("amqj.logging.level", "INFO")

    brokerOptions.setConfigProperty("qpid.work_dir", workDir.getAbsolutePath)
    brokerOptions.setConfigProperty("qpid.amqp_port", s"$amqpPort")
    brokerOptions.setConfigProperty("qpid.http_port", s"$httpPort")
    brokerOptions.setConfigProperty("qpid.home_dir", homeDir.getPath)

    brokerOptions.setInitialConfigurationLocation(s"${homeDir.getPath}${File.separator}$configFileName")
    broker.startup(brokerOptions)
    new EmbeddedAmqpBrokerImpl(broker, tmpFolder, s"amqps://$login:$password@$host:$amqpPort")
  }

  private def makeHomeDir(tmpFolder: File): File = {
    val home = new File(tmpFolder, "qpid")

    def copy(fileName: String): Unit = {
      val to = new File(home, fileName)
      Files.createParentDirs(to)
      to.createNewFile()
      Files.write(Source.fromInputStream(getClass.getResourceAsStream(s"/qpid/$fileName")).mkString, to, StandardCharsets.UTF_8)
    }
    copy("config.json")
    copy("password")
    home
  }

}


trait EmbeddedAmqpBroker {

  def amqpUri: String

  def shutdownBroker(): Unit

}

private class EmbeddedAmqpBrokerImpl(broker: Broker, tmpFolder: File, override val amqpUri: String)
  extends EmbeddedAmqpBroker {

  def shutdownBroker(): Unit = {
    broker.shutdown()
    tmpFolder.delete()
  }
}

