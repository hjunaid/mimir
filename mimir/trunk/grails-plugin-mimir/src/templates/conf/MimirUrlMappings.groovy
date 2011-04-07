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

    // Searching
    //
    // action names that do not start "gus" are mapped to the search
    // controller (the XML search service and the back-end used by
    // RemoteQueryRunner)
    "/$indexId/search/$action?"(controller:"search", parseRequest:true) {
      constraints { action(matches:/^(?!gus).*$/) }
    }

    // action names that start "gus" are mapped to the GUS demo web UI
    "/$indexId/search/$action?/$id?"(controller:"gus") {
      constraints { action(matches:/^gus.*$/) }
    }

    // the top-level "index URL" for a given index *must* be mapped to this
    // action (the plugin assumes this and uses it to generate reverse
    // mappings).
    "/$indexId"(controller:"indexManagement", action:"index")

    // Index management actions (adding documents, closing index, etc.)
    "/$indexId/manage/$action?"(controller:"indexManagement")

    // admin-only actions - CRUD controllers plus index administration

    "/admin"(controller:"mimirStaticPages", action:"admin")
    "/admin/$controller/$action?/$id?"{
      constraints {
        controller(inList:[
          "federatedIndex",
          "localIndex",
          "remoteIndex",
          "indexTemplate"
        ])
      }
    }
    "/admin/actions/$indexId/$action?"(controller:"indexAdmin")
  }
}
