package ru.sbt.qa.alm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.hp.alm.rest.Entity;
import com.hp.alm.rest.Field;
import com.hp.alm.rest.Fields;
import com.hp.alm.rest.Field.Value;

/**
 * Utility class to extract values and conversions of the {@link com.hp.alm.rest.Entity}
 */
public class AlmEntityUtils {
	/**
	 * Returns the value of the field named <i>fieldName</i> of the {@link com.hp.alm.rest.Entity} <i>ent</i>
	 * @param ent
	 * @param fieldName
	 * @return String value of the fieldName
	 */
	public static String getFieldValue(Entity ent,String fieldName) {
		return ent.getFields().getField().stream().
				filter(p -> p.getName().equals(fieldName)).findFirst().map(Field::getValue).get().stream().
				findFirst().map(Value::getValue).get();
	}

	/**
	 * Sets the value of the field named <i>fieldName</i> of the {@link com.hp.alm.rest.Entity} <i>ent</i>
	 * @param ent
	 * @param fieldName
	 * @param newVal
	 * @return ent
	 */
	public static Entity setFieldValue(Entity ent,String fieldName,String newVal) {
		ent.setFields(new Fields().withField(
				ent.getFields().getField().stream()
				.<Field>map(f -> {
					if(f.getName().equals(fieldName)) f=new Field().withName(f.getName()).withValue(new Value().withValue(newVal)); 
					return f;})
				.collect(Collectors.toList())
		));
		return ent;
	}

	/**
	 * Converts {@link com.hp.alm.rest.Entity} ent to the Map&lt;String,String&gt; ,keys are the field names and values are field values
	 * @param ent
	 * @return Map&lt;String,String&gt;
	 */
	public static Map<String,String> entity2Map(Entity ent) {
		Map<String,String> result = new HashMap<>();
		Value emptyVal=new Value().withValue("");
		ent.getFields().getField()
			.forEach(p -> result.put(p.getName(), p.getValue().stream().findFirst().orElse(emptyVal).getValue()));
		return result;
	}

	/**
	 * Converts the Map&lt;String,String&gt; (keys are the field names and values are field values) to the  {@link com.hp.alm.rest.Entity} ent of the <i>type</i>
	 * @param type
	 * @param mflds
	 * @return {@link com.hp.alm.rest.Entity}
	 */
	public static Entity map2Entity(String type,Map<String,String> mflds) {
		List<Field> fields = mflds.entrySet().stream().<Field>map(e -> new Field().withName(e.getKey()).withValue(new Value().withValue(e.getValue()))).collect(Collectors.toList());
		Entity ent = new Entity().withType(type).withFields(new Fields().withField(fields));
		return ent;
	}
}
