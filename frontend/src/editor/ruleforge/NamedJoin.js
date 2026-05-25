/* bootbox is a global */

ruleforge.NamedJoin=function(context){
	this.type="and";
	this.context=context;
	window._VariableValueArray.push(this);
	this.H=30;
	this.children=[];
	this.container=document.createElement("span");
	this.container.className="btn btn-default dropdown-toggle rule-join-container";
	this.container.style.border="solid 1px #2196f3";
	this.referenceName=null,this.variableCategory=null;
	this.namedLabel=generateContainer();
	this.namedLabel.style.color="#9C27B0";
	this.namedLabel.style.cursor="pointer";
	this.namedLabel.style.fontSize="12px";
	this.namedLabel.textContent = "请输入引用名";
	this.container.appendChild(this.namedLabel);
	var namedEditor=document.createElement("input");
	namedEditor.type="text";
	namedEditor.className="form-control";
	namedEditor.style.width="100px";
	namedEditor.style.height="24px";
	this.container.appendChild(namedEditor);
	namedEditor.style.display='none';
	var self=this;
	this.namedLabel.addEventListener('click',function () {
		self.namedLabel.style.display='none';
		namedEditor.style.display="inline";
		namedEditor.value=self.referenceName || '';
		namedEditor.focus();
		self.resetItemPosition(0,null);
	});
	namedEditor.addEventListener('blur',function () {
		if(self.referenceName && self.referenceName.length>0){
			self.context.deleteFromNamedMap(self.referenceName);
		}
		var value=this.value;
		if(value && value!==''){
			self.referenceName=value;
			self.namedLabel.textContent = value+":";
			if(self.variableCategory){
				self.context.putToNamedMap(self.referenceName,self.variableCategory);
			}
		}else{
			self.referenceName=null;
			self.namedLabel.textContent = "请输入引用名";
		}
		for(let refValue of self.context.rule.namedReferenceValues){
			if(refValue){
				refValue.initMenu();
			}
		}
		self.namedLabel.style.display='';
		namedEditor.style.display='none';
		self.resetItemPosition(0,null);
	});

	this.variableCategoryLabel=generateContainer();
	this.variableCategoryLabel.style.color="#03A9F4";
	this.variableCategoryLabel.style.cursor="pointer";
	this.variableCategoryLabel.style.fontSize="12px";
	this.variableCategoryLabel.textContent = "请选择变量对象";
	this.container.appendChild(this.variableCategoryLabel);
	this.initMenu();

	this.joinContainer=document.createElement("span");
	this.container.appendChild(this.joinContainer);
	this.joinLabel=document.createElement("span");
	this.joinLabel.style.fontSize="11pt";
	this.joinLabel.style.color="#FF9800";
	this.joinLabel.textContent="并且";
	this.joinContainer.appendChild(this.joinLabel);
};

ruleforge.NamedJoin.prototype.initMenu=function(variableLibraries){
	var data=window._ruleforgeEditorVariableLibraries;
	if(variableLibraries){
		data=variableLibraries;
	}
	if(!data){
		return;
	}
	var self=this;
	var config={menuItems:[]};
	for(let categories of data){
		for(let category of categories){
			var menuItem={
				label:category.name,
				category:category,
				onClick:function (item) {
					if(self.children.length>0){
						bootbox.confirm("当前节点下已配置了条件，此操作将会清这些条件，你确定吗？",function (result) {
							if(result){
								self.variableCategory=item.category;
								self.variableCategoryName=item.category.name;
								self.context.putToNamedMap(self.referenceName,self.variableCategory);
								self.variableCategoryLabel.textContent = item.label;
								self.resetItemPosition(0,null);
								for(let child of self.children){
									child.remove();
								}
								self.children=[];
							}
						});
					}else{
						self.variableCategory=item.category;
						self.variableCategoryName=item.category.name;
						self.context.putToNamedMap(self.referenceName,self.variableCategory);
						self.variableCategoryLabel.textContent = item.label;
						self.resetItemPosition(0,null);
					}
					for(let refValue of self.context.rule.namedReferenceValues){
						if(refValue){
							refValue.initMenu();
						}
					}
				}
			};
			config.menuItems.push(menuItem);
		}
	}
	if(self.categoryMenu){
		self.categoryMenu.setConfig(config);
	}else{
		self.categoryMenu=new RuleForge.menu.Menu(config);
	}
	this.variableCategoryLabel.addEventListener('click',function(e){
		if(!self.referenceName){
			window.bootbox.alert("请先输入引用名称.");
			return;
		}
		self.categoryMenu.show(e);
	});
	if(this.variableCategoryName){
		for(let categories of data){
			for(let category of categories){
				if(category.name===this.variableCategoryName){
					this.variableCategory=category;
					break;
				}
			}
			if(this.variableCategory){
				break;
			}
		}
		if(this.variableCategory){
			this.context.putToNamedMap(this.referenceName,this.variableCategory);
			for(let conn of this.children){
				conn.getCondition().initMenu();
			}
		}
	}
	for(let refValue of self.context.rule.namedReferenceValues){
		if(refValue){
			refValue.initMenu();
		}
	}
};

ruleforge.NamedJoin.prototype.initData=function(data){
	this.referenceName=data["referenceName"];
	this.variableCategoryName=data["variableCategory"];
	this.namedLabel.textContent = this.referenceName+":";
	this.variableCategoryLabel.textContent = this.variableCategoryName;
	var items=data["items"];
	this.setType(data["junctionType"]);
	if(!items){
		return;
	}
	for(let item of items){
		var newConnection=this.addItem(false);
		newConnection.getCondition().initData(item);
	}
};
ruleforge.NamedJoin.prototype.setType=function(type){
	this.type=type;
	if(type==="or"){
		this.joinLabel.textContent = "或者";
	}else{
		this.joinLabel.textContent = "并且";
	}
	window._setDirty();
};
ruleforge.NamedJoin.prototype.init=function(parentConnection){
	if(parentConnection){
		this.parentConnection=parentConnection;
		this.parent=parentConnection.getParentJoin();
	}
	var joinArrow=document.createElement("i");
	joinArrow.className="glyphicon glyphicon-chevron-down rule-join-node";
	var self=this;
	var onClick=function(menu){
		self.setOperator(menu.name);
	};
	self.menu=new RuleForge.menu.Menu({
		menuItems:[{
			label:"并且",
			onClick:function(){
				self.setType("and");
			}
		},{
			label:"或者",
			onClick:function(){
				self.setType("or");
			}
		},{
			label:"添加条件",
			onClick:function(){
				self.addItem(false);
			}
		},{
			label:"删除",
			onClick:function(){
				if(self.children.length>0){
					window.bootbox.alert("请先删除当前连接下子元素！");
					return;
				}
				if(parentConnection){
					var parentJoin=parentConnection.getParentJoin();
					if(parentJoin){
						parentJoin.removeConnection(parentConnection);
					}
				}
				if(self.referenceName){
					self.context.deleteFromNamedMap(self.referenceName);
				}
				for(let refValue of self.context.rule.namedReferenceValues){
					if(refValue){
						refValue.initMenu();
					}
				}
			}
		}]
	});
	this.joinContainer.addEventListener('click',function(e){
		self.menu.show(e);
	});
	this.joinContainer.appendChild(joinArrow);
};
ruleforge.NamedJoin.prototype.removeConnection=function(connection){
	var pos=this.children.indexOf(connection);
	if(this.children.length>1){
		this.resetItemPosition(pos+1, false);
	}
	connection.remove();
	this.children.splice(pos, 1);
	this.resetContainerSize();
	window._setDirty();
};
ruleforge.NamedJoin.prototype.addItem=function(isJoin){
	if(!this.variableCategoryName || !this.referenceName){
		window.bootbox.alert("请先定义变量引用名及变量对象!");
		return;
	}
	window._setDirty();
	var childrenCount=this.getChildrenCount();
	if(childrenCount>0 && this.parent){
		var parentChildren=this.parent.getChildren();
		var pos=parentChildren.indexOf(this.parentConnection);
		this.parent.resetItemPosition(pos+1,true);
	}
	var totalHeight=childrenCount*this.H;
	var parentLeft=parseInt(this.container.style.left);
	var parentTop=parseInt(this.container.style.top);
	var startX=parentLeft+this.container.offsetWidth-15;
	var startY=parentTop+this.H/5;
	var endX=startX+40;
	var endY=startY+totalHeight;
	if(isJoin){
		endY-=5;
	}
	var connection=new ruleforge.Connection(this.context,isJoin,this);
	connection.drawPath(startX,startY,endX,endY);
	this.children.push(connection);
	this.resetContainerSize();
	return connection;
};
ruleforge.NamedJoin.prototype.toXml=function(){
	if(!this.referenceName || !this.variableCategoryName){
		throw "请定义引用条件信息.";
	}
	var xml="<named-atom junction-type=\""+this.type+"\" reference-name=\""+this.referenceName+"\" var-category=\""+this.variableCategoryName+"\">";
	for(var i=0;i<this.children.length;i++){
		var conn=this.children[i];
		xml+=conn.toXml();
	}
	xml+="</named-atom>";
	return xml;
};
ruleforge.NamedJoin.prototype.resetItemPosition=function(index,add){
	if(index==-1){
		return;
	}
	for(var i=index;i<this.children.length;i++){
		var connection=this.children[i];
		if(add===null){
			var parentLeft=parseInt(this.container.style.left);
			var startX=parentLeft+this.container.offsetWidth-15;
			var endX=startX+40;
			connection.setStartX(startX);
			connection.setEndX(endX);
		}else{
			var offset=this.H;
			if(!add){
				offset=-this.H;
			}
			connection.setEndY(connection.getEndY()+offset);
			if(index==0){
				connection.setStartY(connection.getStartY()+offset);
			}
		}
		connection.update(add);
	}
	if(index>0 && this.parent){
		var parentChildren=this.parent.getChildren();
		var pos=parentChildren.indexOf(this.parentConnection);
		var parentJoin=this.parentConnection.getParentJoin();
		parentJoin.resetItemPosition(pos+1,add);
	}
	window._setDirty();
};
ruleforge.NamedJoin.prototype.resetContainerSize=function(){
	var container=this.context.getCanvas();
	var height=container.style.height;
	height=parseInt(height);
	var childrenCount=this.context.getTotalChildrenCount();
	if(childrenCount==0)childrenCount=1;
	var totalHeight=childrenCount*this.H+10;
	container.style.height=totalHeight+"px";
};
ruleforge.NamedJoin.prototype.getChildrenCount=function(){
	var total=0;
	for(var i=0;i<this.children.length;i++){
		var child=this.children[i].getJoin();
		if(child){
			var count=child.getChildrenCount();
			if(count==0){
				count=1;
			}
			total+=count;
		}else{
			total++;
		}
	}
	return total;
};
ruleforge.NamedJoin.prototype.initTopJoin=function(container){
	var left=5;
	var top=5;
	this.joinContainer.style.position="absolute";
	this.joinContainer.style.left=left;
	this.joinContainer.style.top=top;
	container.appendChild(this.joinContainer);
	this.context.setRootJoin(this);
};
ruleforge.NamedJoin.prototype.getChildren=function(){
	return this.children;
};
ruleforge.NamedJoin.prototype.getContainer=function(){
	return this.container;
};