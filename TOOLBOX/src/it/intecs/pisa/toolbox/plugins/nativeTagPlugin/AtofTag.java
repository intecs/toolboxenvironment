package it.intecs.pisa.toolbox.plugins.nativeTagPlugin;

import it.intecs.pisa.util.DOMUtil;

public class AtofTag extends NativeTagExecutor{
          
	@Override
	public Object executeTag(org.w3c.dom.Element expression) throws Exception{
                  String floatValue;

            floatValue = (String) this.executeChildTag(DOMUtil.getFirstChild(expression));

            return Float.valueOf(floatValue);
    }

   

}
