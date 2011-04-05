package gate.mimir.web

/**
 * No-op controller used for static views in mimir to make URL mapping
 * more straightforward.
 */
class MimirStaticPagesController {
  def index = {}
  
  def error = {}
}
