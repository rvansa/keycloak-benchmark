package org.jboss.perf.util

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.{AtomicInteger, AtomicReferenceArray}

import scala.util.Random

/**
  * Provides random access with fast reads, has bounded capacity.
  *
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
class RandomDataProvider[T >: Null <: AnyRef](
                         seq: IndexedSeq[T],
                         cap: Int = 0
                         ) {
  private val capacity = if (cap > 0) cap else seq.length * 2
  private val freeSlots: Range = seq.length until capacity
  private val data = new AtomicReferenceArray[T](capacity)
  private val underflow = new ArrayBlockingQueue[Int](capacity)
  private val size = new AtomicInteger(seq.length)

  {
    var pos = 0
    for (e <- seq) {
      data.set(pos, e)
      pos = pos + 1
    }
  }

  def +=(elem: T): RandomDataProvider[T] = {
    val pos = underflow.take()
    data.set(pos, elem)
    size.incrementAndGet()
    this
  }

  private def dataIndexOf(elem: T, from: Int): Int = {
    if (from >= data.length()) {
      return -1
    } else if (data.get(from).equals(elem)) {
      return from
    } else {
      return dataIndexOf(elem, from + 1)
    }
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
      val pos = random.nextInt(capacity)
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

  def removeOn(pos: Int): Option[T] = {
    val element: T = data.get(pos)
    if (element != null) {
      if (data.compareAndSet(pos, element, null)) {
        underflow.add(pos);
        size.decrementAndGet();
        return Some(element)
      }
    }
    None
  }
}
