package com.ruleforge.runtime.assertor;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;

import com.ruleforge.Utils;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Op;

/**
 * @author Jacky.gao
 * @since 2015年1月6日
 */
public class GreaterThenAssertor implements Assertor {

	public boolean eval(Object left, Object right,Datatype datatype) {
		if(left==null || right==null){
			return false;
		}
		if(datatype.equals(Datatype.Date)){
			Date leftDate=(Date)datatype.convert(left);
			Date rightDate=(Date)datatype.convert(right);
			Calendar leftCalendar=Calendar.getInstance();
			leftCalendar.setTime(leftDate);
			Calendar rightCalendar=Calendar.getInstance();
			rightCalendar.setTime(rightDate);
			int result=leftCalendar.compareTo(rightCalendar);
			if(result==1){
				return true;
			}
		}else{
			BigDecimal leftNumber=Utils.toBigDecimal(left);
			BigDecimal rightNumber=Utils.toBigDecimal(right);
			int result=leftNumber.compareTo(rightNumber);
			if(result==1){
				return true;
			}
		}
		return false;
	}

	public boolean support(Op op) {
		return op.equals(Op.GreaterThen);
	}
}
