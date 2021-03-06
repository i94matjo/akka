/**
 *  Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.cluster.routing

import com.typesafe.config.ConfigFactory
import akka.actor.Address
import akka.actor.RootActorPath
import akka.testkit.AkkaSpec
import akka.routing.ActorSelectionRoutee
import akka.routing.ActorRefRoutee

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class WeightedRouteesSpec extends AkkaSpec(ConfigFactory.parseString("""
      akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
      akka.remote.netty.tcp.port = 0
      """)) {

  val a1 = Address("akka.tcp", "sys", "a1", 2551)
  val b1 = Address("akka.tcp", "sys", "b1", 2551)
  val c1 = Address("akka.tcp", "sys", "c1", 2551)
  val d1 = Address("akka.tcp", "sys", "d1", 2551)

  val routeeA = ActorSelectionRoutee(system.actorSelection(RootActorPath(a1) / "user" / "a"))
  val routeeB = ActorSelectionRoutee(system.actorSelection(RootActorPath(b1) / "user" / "b"))
  val routeeC = ActorSelectionRoutee(system.actorSelection(RootActorPath(c1) / "user" / "c"))
  val routees = Vector(routeeA, routeeB, routeeC)
  val testActorRoutee = ActorRefRoutee(testActor)

  "WeightedRoutees" must {

    "allocate weighted routees" in {
      val weights = Map(a1 -> 1, b1 -> 3, c1 -> 10)
      val weighted = new WeightedRoutees(routees, a1, weights)

      weighted(1) must be(routeeA)
      2 to 4 foreach { weighted(_) must be(routeeB) }
      5 to 14 foreach { weighted(_) must be(routeeC) }
      weighted.total must be(14)
    }

    "check boundaries" in {
      val empty = new WeightedRoutees(Vector(), a1, Map.empty)
      empty.isEmpty must be(true)
      intercept[IllegalArgumentException] {
        empty.total
      }

      val empty2 = new WeightedRoutees(Vector(routeeA), a1, Map(a1 -> 0))
      empty2.isEmpty must be(true)
      intercept[IllegalArgumentException] {
        empty2.total
      }
      intercept[IllegalArgumentException] {
        empty2(0)
      }

      val weighted = new WeightedRoutees(routees, a1, Map.empty)
      weighted.total must be(3)
      intercept[IllegalArgumentException] {
        weighted(0)
      }
      intercept[IllegalArgumentException] {
        weighted(4)
      }
    }

    "allocate routees for undefined weight" in {
      val weights = Map(a1 -> 1, b1 -> 7)
      val weighted = new WeightedRoutees(routees, a1, weights)

      weighted(1) must be(routeeA)
      2 to 8 foreach { weighted(_) must be(routeeB) }
      // undefined, uses the mean of the weights, i.e. 4
      9 to 12 foreach { weighted(_) must be(routeeC) }
      weighted.total must be(12)
    }

    "allocate weighted local routees" in {
      val weights = Map(a1 -> 2, b1 -> 1, c1 -> 10)
      val routees2 = Vector(testActorRoutee, routeeB, routeeC)
      val weighted = new WeightedRoutees(routees2, a1, weights)

      1 to 2 foreach { weighted(_) must be(testActorRoutee) }
      3 to weighted.total foreach { weighted(_) must not be (testActorRoutee) }
    }

    "not allocate ref with weight zero" in {
      val weights = Map(a1 -> 0, b1 -> 2, c1 -> 10)
      val weighted = new WeightedRoutees(routees, a1, weights)

      1 to weighted.total foreach { weighted(_) must not be (routeeA) }
    }

  }
}
