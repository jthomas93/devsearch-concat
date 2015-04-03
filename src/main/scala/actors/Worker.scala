package actors

import java.io._
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

import actors.Coordinator.{FileResponse, BlobResponse, Shutdown}
import akka.actor.{ActorRef, Actor, ActorLogging, Props}
import scala.language.implicitConversions

/**
 * The worker actor is the one in charge of creating the large
 * files from smaller ones.
 *
 * @param master The actor to which it has to request new files
 */
class Worker(master: ActorRef) extends Actor with ActorLogging {

  import Worker._

  /* The stream we are currently writing in */
  var currentStream = Option.empty[FileChannel]
  var currentBlob = Option.empty[File]

  /* The maximum number of bytes we are allowed to write to the stream */
  var maxSize = 0L
  /* The number of bytes written so far */
  var bytesWritten = 0L

  /* Temporary file we have not written yet */
  var tempFile = Option.empty[File]

  def receive = {
    /* Start to work */
    case Begin => master ! BlobRequest

    /* New blob to be created, it will contain the concatenation of many files */
    case BlobResponse(file, size) => {
      assert(currentStream == None)
      // TODO: check output
      file.createNewFile()
      currentBlob = Some(file)
      maxSize = size
      bytesWritten = 0
      currentStream = Some(new FileOutputStream(file).getChannel)
      tempFile.map(self ! FileResponse(_)).getOrElse(master ! FileRequest)
    }

    /* One single file to append to the current blob */
    case FileResponse(file) => {
      val fileSize = file.length()
      val header = s"$fileSize:$file\n".getBytes("UTF-8")
      val separator = "\n".getBytes("UTF-8")
      val bytesToWrite = header.length + fileSize + separator.length

      if (bytesToWrite + bytesWritten > maxSize) {
        /* The blob is full */
        tempFile = Some(file)
        currentStream.foreach {
          _.close
        }
        currentStream = None
        log.info(s"Finished with ${currentBlob.get}")
        currentBlob = None
        master ! BlobRequest
      } else {
        /* Append the current file */
        bytesWritten += bytesToWrite
        tempFile = None
        assert(currentStream.isDefined)
        currentStream.foreach { dst =>
          dst.write(header)
          val src = new FileInputStream(file).getChannel
          var size = src.size
          if(size != file.length) log.error(s"Problem with size : $size")
          while(size > 0){
            size -= dst.transferFrom(src, src.size - size, size)
          }
          dst.force(true)
          src.force(true)
          src.close()
          dst.write(separator)
        }
        master ! FileRequest
      }
    }

    /* No more files, end what you are doing and send finished message */
    case Shutdown => {
      currentStream.foreach {
        _.close()
      }
      log.info(s"Closing last blob : ${currentBlob.get}")
      sender ! Finished
    }
  }
}

object Worker {
  def props(reader: ActorRef) = Props(new Worker(reader))

  case object FileRequest

  case object BlobRequest

  case object Begin

  case object Finished

  /** Implicit definition to transform byte arrays to byte buffers */
  implicit def byteArr2byteBuff(arr : Array[Byte]): ByteBuffer = ByteBuffer.wrap(arr)
}
