/**
 * These are the default URL mappings for Mimir actions.  You may change
 * them if you wish but the views and the remote indexing and searching
 * protocol make a number of assumptions about the mappings, so be sure
 * you know what you are doing before you change things.  Any change must
 * ensure that the relative paths between the various mappings stay the
 * same.  For example it is safe to add a fixed prefix to all of the
 * standard mappings ("/mimir/$indexId", "/mimir/remote/..." etc.).
 */
class MimirUrlMappings {
    static mappings = {
      // static view mappings
      "/"(controller:"mimirStaticPages", action:"index")
      "500"(controller:"mimirStaticPages", action:"error")

      // these constraints are used in several mappings below
      def isNotBin = {
        constraints {
          action(matches:/.*(?<!Bin)$/)
        }
      }
      
      def isBin = {
        constraints {
          action(matches:/.*Bin$/)
        }
      }
  
      // public actions - non-Bin search API, GUS and buildIndex, plus
      // index info
      
      "/$indexId/search/$action?"(controller:"search", parseRequest:true, isNotBin)

      "/$indexId/buildIndex/$action"(controller:"buildIndex", isNotBin)

      "/$indexId/gus/$action?/$id?"(controller:"gus")

      "/$indexId"(controller:"indexManagement", action:"info")
      
      // admin-only actions - CRUD controllers plus index management
      
      "/admin/$controller/$action?/$id?"{
        constraints {
          controller(inList:["federatedIndex", "localIndex", "remoteIndex",
                             "indexTemplate"])
        }
      }
      
      "/admin/$indexId/manage/$action?"(controller:"indexManagement", isNotBin)
      
      // Remote protocol actions (essentially anything that ends Bin)
      "/remote/$indexId/search/$action?"(controller:"search", parseRequest:true, isBin)
      "/remote/$indexId/buildIndex/$action"(controller:"buildIndex", isBin)
      "/remote/$indexId/manage/$action?"(controller:"indexManagement", isBin)
      "/remote/$indexId"(controller:"indexManagement", action:"infoBin")
      
	}
}
