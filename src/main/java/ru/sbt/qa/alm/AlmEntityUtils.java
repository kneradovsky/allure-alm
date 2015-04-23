package ru.sbt.qa.alm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.hp.alm.rest.Entity;
import com.hp.alm.rest.Field;
import com.hp.alm.rest.Fields;
import com.hp.alm.rest.Field.Value;

public class AlmEntityUtils {
	public static String getFieldValue(Entity ent,String fieldName) {
		return ent.getFields().getField().stream().
				filter(p -> p.getName().equals("id")).findFirst().map(Field::getValue).get().stream().
				findFirst().map(Value::getValue).get();
	}
	
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
	
	public static Map<String,String> entity2Map(Entity ent) {
		Map<String,String> result = new HashMap<>();
		Value emptyVal=new Value().withValue("");
		ent.getFields().getField()
			.forEach(p -> result.put(p.getName(), p.getValue().stream().findFirst().orElse(emptyVal).getValue()));
		return result;
	}
	
	public static Entity map2Entity(String type,Map<String,String> mflds) {
		List<Field> fields = mflds.entrySet().stream().<Field>map(e -> new Field().withName(e.getKey()).withValue(new Value().withValue(e.getValue()))).collect(Collectors.toList());
		Entity ent = new Entity().withType(type).withFields(new Fields().withField(fields));
		return ent;
	}
}
