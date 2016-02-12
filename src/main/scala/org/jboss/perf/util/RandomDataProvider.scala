package org.jboss.perf.util

import java.util.Objects
import java.util.concurrent.atomic.{AtomicInteger, AtomicReferenceArray}

import scala.util.Random

/**
  * Provides random access with fast reads. Writes can block another writes.
  *
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
class RandomDataProvider[T >: Null <: AnyRef](
                         seq: IndexedSeq[T],
                         cap: Int = 0
                         ) {
  private var capacity = if (cap > 0) cap else seq.length * 2
  private var data = new AtomicReferenceArray[T](capacity)
  private val size = new AtomicInteger(seq.length)

  {
    var pos = 0
    for (e <- seq) {
      data.set(pos, e)
      pos = pos + 1
    }
  }

  private def dataIndexOf(elem: T, from: Int): Int = {
    if (from >= data.length()) {
      return -1
    } else if (Objects.equals(data.get(from), elem)) {
      return from
    } else {
      return dataIndexOf(elem, from + 1)
    }
  }

  def +=(elem: T): RandomDataProvider[T] = this.synchronized {
    var pos = dataIndexOf(null, 0)
    if (pos >= 0) {
      data.set(pos, elem)
      size.incrementAndGet()
    } else {
      val tmp = new AtomicReferenceArray[T](capacity * 2)
      for (i <- 0 until capacity) {
        tmp.set(i, data.get(i))
      }
      data = tmp;
      capacity = tmp.length()
    }
    this
  }

  def -=(elem: T): RandomDataProvider[T] = {
    var pos = dataIndexOf(elem, 0)
    if (pos >= 0) {
      removeOn(pos)
    }
    this
  }

  def iterator(rand: Random): Iterator[T] = new Iterator[T] {
    override def hasNext: Boolean = true

    override def next(): T = random(rand)
  }

  def random(random: Random): T = {
    while (true) {
      val data = this.data;
      val pos = random.nextInt(data.length())
      val element: T = data.get(pos)
      if (element != null) {
        return element
      }
    }
    null
  }

  def randomRemove(random: Random): T = {
    do {
      val pos = random.nextInt(capacity)
      removeOn(pos) match {
        case Some(toReturn) => return toReturn
        case None =>
      }
    } while (size.get() > 0)
    null
  }

  def removeOn(pos: Int): Option[T] = this.synchronized {
    val data = this.data;
    if (pos >= data.length()) {
      None
    }
    val element: T = data.get(pos)
    if (element != null) {
      if (data.compareAndSet(pos, element, null)) {
        size.decrementAndGet();
        return Some(element)
      }
    }
    None
  }
}
