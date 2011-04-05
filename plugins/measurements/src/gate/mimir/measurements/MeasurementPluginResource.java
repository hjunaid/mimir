package gate.mimir.measurements;

import gate.Resource;
import gate.creole.AbstractResource;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleResource;

/**
 * A simple no-function CREOLE resource only used so that we have some CREOLE
 * metadata. We use that to locate the JAR containing the classes, which we 
 * then use to locate the associated resource files.   
 */
@CreoleResource(isPrivate=true, comment="A resource type only defined to use " +
		"its configuration side effects. It cannot be instantiated!")
public class MeasurementPluginResource extends AbstractResource {

  private static final long serialVersionUID = -5800095942127931219L;

  @Override
  public Resource init() throws ResourceInstantiationException {
    throw new ResourceInstantiationException("This resource has no actual " +
    		"function and cannot be instantaited.");
  }
  
}
