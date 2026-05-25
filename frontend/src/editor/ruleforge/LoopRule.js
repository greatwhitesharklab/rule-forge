/* bootbox is a global */
import Sortable from 'sortablejs';

ruleforge.LoopRule=function(parent,container,data){
	this.uuid=Math.uuid();
	this.namedMap=new Map();
	this.namedReferenceValues=[];
	container.id=this.uuid;
	this.parent=parent;
	this.container=container;
	this.data=data;
	this.actions=[];
	this.elseActions=[];
	this.loopStartActions=[];
	this.loopEndActions=[];
	this.properties=[];
	this.init();
	this.initData();
};
ruleforge.LoopRule.prototype.init=function(){
	this.ruleContainer=document.createElement("div");
	this.container.appendChild(this.ruleContainer);
	this.initRemark();
	this.initHeader();
    this.initLoopTarget();
    this.initLoopStart();
	this.initIf();
	this.initThen();
	this.initElse();
	this.initLoopEnd();
};
ruleforge.LoopRule.prototype.initData=function(){
	if(!this.data){
		return;
	}
	this.name=this.data["name"];
	this.nameLabel.innerText=this.name;
	var salience=this.data["salience"];
	if(salience){
		this.addProperty(new ruleforge.RuleProperty(this,"salience",salience,1));
	}
	var loop=this.data["loop"];
	if(loop!=null){
		this.addProperty(new ruleforge.RuleProperty(this,"loop",loop,3));
	}
	var effectiveDate=this.data["effectiveDate"];
	if(effectiveDate){
		this.addProperty(new ruleforge.RuleProperty(this,"effective-date",effectiveDate,2));
	}
	var expiresDate=this.data["expiresDate"];
	if(expiresDate){
		this.addProperty(new ruleforge.RuleProperty(this,"expires-date",expiresDate,2));
	}
	var enabled=this.data["enabled"];
	if(enabled!=null){
		this.addProperty(new ruleforge.RuleProperty(this,"enabled",enabled,3));
	}
	var debug=this.data["debug"];
	if(debug!=null){
		this.addProperty(new ruleforge.RuleProperty(this,"debug",debug,3));
	}
	var activationGroup=this.data["activationGroup"];
	if(activationGroup){
		this.addProperty(new ruleforge.RuleProperty(this,"activation-group",activationGroup,1));
	}
	var agendaGroup=this.data["agendaGroup"];
	if(agendaGroup){
		this.addProperty(new ruleforge.RuleProperty(this,"agenda-group",agendaGroup,1));
	}
	var autoFocus=this.data["autoFocus"];
	if(autoFocus!=null){
		this.addProperty(new ruleforge.RuleProperty(this,"auto-focus",autoFocus,3));
	}
	var ruleflowGroup=this.data["ruleflowGroup"];
	if(ruleflowGroup){
		this.addProperty(new ruleforge.RuleProperty(this,"ruleflow-group",autoFocus,1));
	}
    var remark=this.data["remark"];
    this.remark.setData(remark);

    var loopTarget=this.data["loopTarget"];
    if(loopTarget){
        var value=loopTarget.value;
        if(value){
            var valueType=value.valueType;
            this.loopTargetInputType.setValueType(valueType,value);
        }
    }

    var loopStart=this.data["loopStart"];
    if(loopStart){
        var actions=loopStart.actions;
        if(actions){
            for(var i=0;i<actions.length;i++){
                var action=actions[i];
                this.addLoopStartAction(action);
            }
        }
    }

	var lhs=this.data["lhs"];
	if(lhs){
		var criterion=lhs["criterion"];
		if(criterion){
			this.initCriterion(criterion);
		}else{
			this.initCriterion();
		}
	}else{
		this.initCriterion();
	}
	var rhs=this.data["rhs"];
	if(rhs){
		var actions=rhs["actions"];
		if(actions){
			for(var i=0;i<actions.length;i++){
				var action=actions[i];
				this.addAction(action);
			}
		}
	}
	var elseData=this.data["other"];
	if(elseData){
		var actions=elseData["actions"];
		if(actions){
			for(var i=0;i<actions.length;i++){
				var action=actions[i];
				this.addAction(action,true);
			}
		}
	}

    var loopEnd=this.data["loopEnd"];
    if(loopEnd){
        var actions=loopEnd.actions;
        if(actions){
            for(var i=0;i<actions.length;i++){
                var action=actions[i];
                this.addLoopEndAction(action);
            }
        }
    }
};
ruleforge.LoopRule.prototype.initTopJoin=function(){
	var context=new ruleforge.Context(this.conditionContainer,this);
	this.join=new ruleforge.Join(context);
	this.join.init(null);
	this.join.initTopJoin(this.conditionContainer);
	this.join.setType("and");
};
ruleforge.LoopRule.prototype.initCriterion=function(criterion){
	var context=new ruleforge.Context(this.conditionContainer,this);
	this.join=new ruleforge.Join(context);
	this.join.init(null);
	this.join.initTopJoin(this.conditionContainer);
	var junctionType="and";
	if(criterion){
		junctionType=criterion["junctionType"];
	}
	this.join.setType(junctionType);
	if(criterion){
		this.join.initData(criterion);
	}
};
ruleforge.LoopRule.prototype.addProperty=function(property){
	this.propertyContainer.appendChild(property.getContainer());
	this.properties.push(property);
	window._setDirty();
};

ruleforge.LoopRule.prototype.initRemark=function(){
	var remarkContainer=document.createElement("div");
	this.remark=new Remark(remarkContainer);
	this.ruleContainer.appendChild(remarkContainer);
};

ruleforge.LoopRule.prototype.initHeader=function(){
	this.nameContainer=document.createElement("div");
	this.ruleContainer.appendChild(this.nameContainer);
	this.label=document.createElement("span");
	this.label.style.lineHeight="30px";
	this.label.innerHTML="<Strong>循环规则 <Strong>";
	this.nameContainer.appendChild(this.label);
	this.nameEditor=document.createElement("input");
	this.nameEditor.type="text";
	this.nameEditor.className="form-control rule-text-editor";
	this.nameEditor.style.display="none";
	this.name="rule";
	this.nameLabel=document.createElement("span");
	this.nameLabel.textContent=this.name;
	this.label.appendChild(this.nameEditor);
	this.label.appendChild(this.nameLabel);
	var self=this;
	this.nameLabel.addEventListener('click',function(){
		self.nameLabel.style.display='none';
		self.nameEditor.style.display='';
		self.nameEditor.value=self.name;
		self.nameEditor.focus();
	});

	this.nameEditor.addEventListener('blur',function(){
		self.name=self.nameEditor.value;
		self.nameEditor.style.display='none';
		self.nameLabel.style.display='';
		self.nameLabel.textContent = self.name;
		window._setDirty();
	});
	this.nameEditor.style.display="none";
	var del=document.createElement("i");
	del.className="glyphicon glyphicon-remove rule-delete";
	del.style.verticalAlign="middle";
	del.style.cursor="pointer";
	del.addEventListener('click',function(){
		const msg="真的要删除规则"+self.name+"？";
		MsgBox.confirm(msg,function(){
			var pos=self.parent.rules.indexOf(self);
			self.parent.rules.splice(pos,1);
			self.container.remove();
			window._setDirty();
		});
	});

	this.nameContainer.appendChild(del);

	this.propertyContainer=document.createElement("span");
	this.propertyContainer.style.padding="10px";

	var addProp=document.createElement("span");
	addProp.className="rule-add-property";
	addProp.textContent="添加属性";
	var onClick=function(menuItem){
		var prop=new ruleforge.RuleProperty(self,menuItem.name,menuItem.defaultValue,menuItem.editorType);
		self.addProperty(prop);
	};
	self.menu=new RuleForge.menu.Menu({
		menuItems:[{
			label:"优先级",
			name:"salience",
			defaultValue:"10",
			editorType:1,
			onClick:onClick
		},{
			label:"生效日期",
			name:"effective-date",
			defaultValue:"",
			editorType:2,
			onClick:onClick
		},{
			label:"失效日期",
			name:"expires-date",
			defaultValue:"",
			editorType:2,
			onClick:onClick
		},{
			label:"是否启用",
			name:"enabled",
			defaultValue:true,
			editorType:3,
			onClick:onClick
		},{
			label:"允许调试信息输出",
			name:"debug",
			defaultValue:true,
			editorType:3,
			onClick:onClick
		},{
			label:"互斥组",
			name:"activation-group",
			defaultValue:"",
			editorType:1,
			onClick:onClick
		},{
			label:"执行组",
			name:"agenda-group",
			defaultValue:"",
			editorType:1,
			onClick:onClick
		},{
			label:"自动获取焦点",
			name:"auto-focus",
			defaultValue:true,
			editorType:3,
			onClick:onClick
		}]
	});
	addProp.addEventListener('click',function(e){
		self.menu.show(e);
	});
	this.ruleContainer.appendChild(addProp);
	this.ruleContainer.appendChild(this.propertyContainer);
};

ruleforge.LoopRule.prototype.initLoopStart=function(){
	this.loopStartLabel=document.createElement("span");
	this.loopStartLabel.innerHTML="<strong>开始前动作</strong>";
	this.ruleContainer.appendChild(this.loopStartLabel);
	this.loopStartActionContainer=document.createElement("div");
	this.loopStartActionContainer.style.padding="5px";
	const _this=this;
	Sortable.create(this.loopStartActionContainer, {
		delay: 200,
		onEnd: function (evt) {
			if (evt.oldIndex !== evt.newIndex) {
				var children=_this.loopStartActionContainer.querySelectorAll("div");
				children.forEach(function(div, index){
					var id=div.id,actions=_this.loopStartActions,targetAction=null;
					for(let action of actions){
						if(action.uuid===id){
							targetAction=action;
							break;
						}
					}
					if(targetAction){
						const pos=actions.indexOf(targetAction);
						actions.splice(pos,1);
						actions.splice(index,0,targetAction);
					}
				});
				window._setDirty();
			}
		}
	});
	this.ruleContainer.appendChild(this.loopStartActionContainer);
	this.addLoopStartActionButton=document.createElement("span");
	this.addLoopStartActionButton.className="rule-add-action";
	this.addLoopStartActionButton.textContent="添加动作";
	var self=this;
	this.addLoopStartActionButton.addEventListener('click',function(){
		self.addLoopStartAction();
	});
	this.loopStartLabel.appendChild(this.addLoopStartActionButton);
};

ruleforge.LoopRule.prototype.addLoopStartAction=function(data){
	var self=this;
	var actionDiv=document.createElement("div");
	actionDiv.style.padding="5px";
	var del=document.createElement("i");
	del.className="glyphicon glyphicon-remove rule-delete-action";
	actionDiv.appendChild(del);
	var action=new ruleforge.ActionType(actionDiv);
	del.addEventListener('click',function(){
		var pos=self.loopStartActions.indexOf(action);
		self.loopStartActions.splice(pos, 1);
		actionDiv.remove();
		window._setDirty();
	});
	this.loopStartActions.push(action);
	this.loopStartActionContainer.appendChild(actionDiv);
	if(data){
		action.initData(data);
	}
	window._setDirty();
};

ruleforge.LoopRule.prototype.initIf=function(){
    this.ifLabel=document.createElement("div");
    this.ifLabel.style.marginLeft="5px";
    this.ifLabel.style.color="#337ab7";
    this.ifLabel.innerHTML="<strong>如果</strong>";
    this.ruleContainer.appendChild(this.ifLabel);
    this.conditionContainer=document.createElement("div");
    this.conditionContainer.style.marginLeft="5px";
    this.conditionContainer.style.height="40px";
    this.conditionContainer.style.position="relative";
    this.ruleContainer.appendChild(this.conditionContainer);
};
ruleforge.LoopRule.prototype.initLoopTarget=function(){
    var loopTargetLabel=document.createElement("div");
    loopTargetLabel.innerHTML="<strong>循环对象</strong>";
    this.ruleContainer.appendChild(loopTargetLabel);
    this.loopTargetContainer=document.createElement("div");
    this.loopTargetContainer.style.padding="5px";
    this.ruleContainer.appendChild(this.loopTargetContainer);
    this.loopTargetInputType = new ruleforge.InputType();
    this.loopTargetContainer.appendChild(this.loopTargetInputType.getContainer());
};

ruleforge.LoopRule.prototype.initThen=function(){
	this.thenLabel=document.createElement("span");
	this.thenLabel.style.marginLeft="5px";
	this.thenLabel.style.color="#337ab7";
	this.thenLabel.innerHTML="<strong>那么</strong>";
	this.ruleContainer.appendChild(this.thenLabel);
	this.actionContainer=document.createElement("div");
	this.actionContainer.style.padding="5px";
	this.ruleContainer.appendChild(this.actionContainer);

	const _this=this;
	Sortable.create(this.actionContainer, {
		delay: 200,
		onEnd: function (evt) {
			if (evt.oldIndex !== evt.newIndex) {
				var children=_this.actionContainer.querySelectorAll("div");
				children.forEach(function(div, index){
					var id=div.id,actions=_this.actions,targetAction=null;
					for(let action of actions){
						if(action.uuid===id){
							targetAction=action;
							break;
						}
					}
					if(targetAction){
						const pos=actions.indexOf(targetAction);
						actions.splice(pos,1);
						actions.splice(index,0,targetAction);
					}
				});
				window._setDirty();
			}
		}
	});

	this.addActionButton=document.createElement("span");
	this.addActionButton.className="rule-add-action";
	this.addActionButton.textContent="添加动作";
	var self=this;
	this.addActionButton.addEventListener('click',function(){
		self.addAction();
	});
	this.thenLabel.appendChild(this.addActionButton);
};
ruleforge.LoopRule.prototype.initElse=function(){
	this.elseContainer=document.createElement("div");
	this.elseContainer.style.marginTop="5px";
	this.ruleContainer.appendChild(this.elseContainer);
	this.elseLabel=document.createElement("span");
	this.elseLabel.style.marginLeft="5px";
	this.elseLabel.style.color="#337ab7";
	this.elseLabel.innerHTML="<strong>否则</strong>";
	this.elseContainer.appendChild(this.elseLabel);
	this.elseActionContainer=document.createElement("div");
	this.elseActionContainer.style.padding="5px";
	this.elseContainer.appendChild(this.elseActionContainer);

	const _this=this;
	Sortable.create(this.elseActionContainer, {
		delay: 200,
		onEnd: function (evt) {
			if (evt.oldIndex !== evt.newIndex) {
				var children=_this.elseActionContainer.querySelectorAll("div");
				children.forEach(function(div, index){
					var id=div.id,actions=_this.elseActions,targetAction=null;
					for(let action of actions){
						if(action.uuid===id){
							targetAction=action;
							break;
						}
					}
					if(targetAction){
						const pos=actions.indexOf(targetAction);
						actions.splice(pos,1);
						actions.splice(index,0,targetAction);
					}
				});
				window._setDirty();
			}
		}
	});

	this.addElseActionButton=document.createElement("span");
	this.addElseActionButton.className="rule-add-action";
	this.addElseActionButton.textContent="添加动作";
	var self=this;
	this.addElseActionButton.addEventListener('click',function(){
		self.addAction(null,true);
	});
	this.elseLabel.appendChild(this.addElseActionButton);
};
ruleforge.LoopRule.prototype.addAction=function(data,iselse){
	var self=this;
	var actionDiv=document.createElement("div");
	actionDiv.style.padding="5px";
	var del=document.createElement("i");
	del.className="glyphicon glyphicon-remove rule-delete-action";
	actionDiv.appendChild(del);
	var action=new ruleforge.ActionType(actionDiv,this);
	del.addEventListener('click',function(){
		if(iselse){
			var pos=self.elseActions.indexOf(action);
			self.elseActions.splice(pos, 1);
		}else{
			var pos=self.actions.indexOf(action);
			self.actions.splice(pos, 1);
		}
		actionDiv.remove();
		window._setDirty();
	});
	if(iselse){
		this.elseActions.push(action);
		this.elseActionContainer.appendChild(actionDiv);
	}else{
		this.actions.push(action);
		this.actionContainer.appendChild(actionDiv);
	}
	if(data){
		action.initData(data);
	}
	window._setDirty();
};


ruleforge.LoopRule.prototype.initLoopEnd=function(){
	this.loopEndLabel=document.createElement("span");
	this.loopEndLabel.innerHTML="<strong>结束后动作</strong>";
	this.ruleContainer.appendChild(this.loopEndLabel);
	this.loopEndActionContainer=document.createElement("div");
	this.loopEndActionContainer.style.padding="5px";
	this.ruleContainer.appendChild(this.loopEndActionContainer);

	const _this=this;
	Sortable.create(this.loopEndActionContainer, {
		delay: 200,
		onEnd: function (evt) {
			if (evt.oldIndex !== evt.newIndex) {
				var children=_this.loopEndActionContainer.querySelectorAll("div");
				children.forEach(function(div, index){
					var id=div.id,actions=_this.loopEndActions,targetAction=null;
					for(let action of actions){
						if(action.uuid===id){
							targetAction=action;
							break;
						}
					}
					if(targetAction){
						const pos=actions.indexOf(targetAction);
						actions.splice(pos,1);
						actions.splice(index,0,targetAction);
					}
				});
				window._setDirty();
			}
		}
	});

	this.addLoopEndActionButton=document.createElement("span");
	this.addLoopEndActionButton.className="rule-add-action";
	this.addLoopEndActionButton.textContent="添加动作";
	var self=this;
	this.addLoopEndActionButton.addEventListener('click',function(){
		self.addLoopEndAction();
	});
	this.loopEndLabel.appendChild(this.addLoopEndActionButton);
};

ruleforge.LoopRule.prototype.addLoopEndAction=function(data){
	var self=this;
	var actionDiv=document.createElement("div");
	actionDiv.style.padding="5px";
	var del=document.createElement("i");
	del.className="glyphicon glyphicon-remove rule-delete-action";
	actionDiv.appendChild(del);
	var action=new ruleforge.ActionType(actionDiv);
	del.addEventListener('click',function(){
		var pos=self.loopEndActions.indexOf(action);
		self.loopEndActions.splice(pos, 1);
		actionDiv.remove();
		window._setDirty();
	});
	this.loopEndActions.push(action);
	this.loopEndActionContainer.appendChild(actionDiv);
	if(data){
		action.initData(data);
	}
	window._setDirty();
};

ruleforge.LoopRule.prototype.toXml=function(){
	var xml="<loop-rule name=\""+this.name+"\"";
	for(var i=0;i<this.properties.length;i++){
		var prop=this.properties[i];
		xml+=" "+prop.toXml();
	}
	xml+=">";
    xml+=this.remark.toXml();
    var loopTargetValue=this.loopTargetInputType.toXml();
    if(loopTargetValue===""){
        throw "循环规则的【循环对象】必须要定义";
    }
    xml+="<loop-target>"+loopTargetValue+"</loop-target>";
    if(this.loopStartActions.length>0){
        xml+="<loop-start>";
        for(var i=0;i<this.loopStartActions.length;i++){
            var action=this.loopStartActions[i];
            xml+=action.toXml();
        }
        xml+="</loop-start>";
    }
	xml+="<if>";
	xml+=this.join.toXml();
	xml+="</if>";
	xml+="<then>";
	for(var i=0;i<this.actions.length;i++){
		var action=this.actions[i];
		xml+=action.toXml();
	}
	xml+="</then>";
	xml+="<else>";
	for(var i=0;i<this.elseActions.length;i++){
		var action=this.elseActions[i];
		xml+=action.toXml();
	}
	xml+="</else>";
    if(this.loopEndActions.length>0){
        xml+="<loop-end>";
        for(var i=0;i<this.loopEndActions.length;i++){
            var action=this.loopEndActions[i];
            xml+=action.toXml();
        }
        xml+="</loop-end>";
    }
	xml+="</loop-rule>";
	return xml;
};