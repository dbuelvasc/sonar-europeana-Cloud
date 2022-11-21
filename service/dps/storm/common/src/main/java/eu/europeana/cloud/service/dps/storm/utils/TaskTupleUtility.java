package eu.europeana.cloud.service.dps.storm.utils;

import eu.europeana.cloud.service.dps.PluginParameterKeys;
import eu.europeana.cloud.service.dps.storm.StormTaskTuple;

/**
 * Created by Tarek on 10/15/2015.
 */
public final class TaskTupleUtility {

  //check it parameter is provided in StormTaskTuple
  protected static boolean isProvidedAsParameter(StormTaskTuple stormTaskTuple, String parameter) {
    if (stormTaskTuple.getParameter(parameter) != null) {
      return true;
    } else {
      return false;
    }
  }

  //if the parameter was provided explicity in StormTaskTuple its value will be returned or its default value provided in PluginParameterKeys will be returned
  public static String getParameterFromTuple(StormTaskTuple stormTaskTuple, String parameter) {
    String outputValue = PluginParameterKeys.PLUGIN_PARAMETERS.get(parameter);
    if (isProvidedAsParameter(stormTaskTuple, parameter)) {
      outputValue = stormTaskTuple.getParameter(parameter);
    }
    return outputValue;
  }
}
