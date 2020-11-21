package elasticsearch

import com.sksamuel.elastic4s.HitReader
import models.Identified

object Instances {
  implicit def identifiedReader[A: HitReader](implicit A: HitReader[A]): HitReader[Identified[A]] =
    hit => A.read(hit).map(result => Identified(hit.id, result))
}
