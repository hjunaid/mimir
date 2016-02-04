modules = {
  mimirLayout {
    resource url:[plugin:'mimir-web', dir:'css', file:'mimir.css']
    resource url:[plugin:'mimir-web', dir:'images', file:'mimir-favicon.ico'], attrs:[type:'ico']
    resource url:[plugin:'mimir-web', dir:'images', file:'mimir-apple-touch-icon.png'], attrs:[rel:'apple-touch-icon']
    resource url:[plugin:'mimir-web', dir:'images', file:'mimir-apple-touch-icon-retina.png'], attrs:[rel:'apple-touch-icon', sizes:'114x114']
    resource url:[plugin:'mimir-web', dir:'js', file:'application.js']
  }
}
