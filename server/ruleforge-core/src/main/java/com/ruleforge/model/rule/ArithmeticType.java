package com.ruleforge.model.rule;

import com.ruleforge.exception.RuleException;

/**
 * @author Jacky.gao
 * @since 2014年12月25日
 */
public enum ArithmeticType {
	Add{
		@Override
		public String toString() {
			return "+";
		}
	},
	Sub{
		@Override
		public String toString() {
			return "-";
		}
	},
	Mul{
		@Override
		public String toString() {
			return "*";
		}
	},
	Div{
		@Override
		public String toString() {
			return "/";
		}
	},
	Mod{
		@Override
		public String toString() {
			return "%";
		}
	};
	public static ArithmeticType parse(String type){
		if(type.equals("+")){
			return Add;
		}else if(type.equals("-")){
			return Sub;
		}else if(type.equals("*")){
			return Mul;
		}else if(type.equals("/")){
			return Div;
		}else if(type.equals("%")){
			return Mod;
		}
		throw new RuleException("Unsupport arithmetic type ["+type+"]");
	}
}
