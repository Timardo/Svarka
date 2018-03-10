package ru.svarka.configuration;

import java.util.ArrayList;
import java.util.HashSet;
import ru.svarka.Svarka;

public class IntArraySetting extends ArraySetting<Integer> 
{
    public IntArraySetting(ConfigBase config, String path, String def, String description)
    {
        super(path, def, description, config);
    }


	@Override
	public void initArr(String array) {
		String[] potential_values = array.split(",");
		
		this.value_array = new ArrayList<Integer>(potential_values.length);
		this.value_set = new HashSet<Integer>(potential_values.length);
		
		for(String potval : potential_values)
		{
			try 
			{
				if(potval.length() == 0)
					continue;
				
				this.value_array.add(Integer.parseInt(potval));
			} 
			catch ( Throwable t) 
			{
				Svarka.LOG.info("Failed to add an option from config file");
				t.printStackTrace();
			}
		}
		this.value_set.addAll(this.value_array);
	}
}
