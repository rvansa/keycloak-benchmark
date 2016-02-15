package org.jboss.perf

import io.gatling.core.session._
import io.gatling.core.validation.{Failure, Success, Validation}
import org.jboss.perf.model.User

private class UserExpression(val variable: String) extends Expression[User] {
  override def apply(s: Session): Validation[User] = s(variable).validate[User]
  def id: IdExpression = new IdExpression(s => apply(s))
  def username: Expression[String] = s => apply(s).map(u => u.username)
}

// the below is a bit of overkill, but something like that might be useful for more complicated scenarios
private class IdExpression(expr: Expression[User]) extends Expression[String] {
  override def apply(s: Session): Validation[String] with IdSettable = expr(s) match {
    case Success(user) => new IdSuccess(user)
    case Failure(msg) => new IdFailure(msg)
  }
}

private trait IdSettable {
  def apply(id: String)
}

private class IdSuccess(user: User) extends Success[String](user.id) with IdSettable {
  def apply(id: String) = (user.id = id)
}

private class IdFailure(msg: String) extends Failure(msg) with IdSettable {
  def apply(id: String) = throw new UnsupportedOperationException()
}