package elasticsearch

object Pagination {

  val PageSize                     = 20
  def pageToOffset(page: Int): Int = (page - 1) * PageSize

  case class Page[A](items: Seq[A], pageNumber: Int, total: Int) {
    def lastPage: Int = ((total - 1) / PageSize) + 1
  }
}
