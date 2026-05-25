/* bootbox is a global */
import BaseNode from './BaseNode.js';

export default class ConditionNode extends BaseNode{
    constructor(context,parentNode){
        super(context,parentNode);
        this.init();
    }
    init(){
        this.nodeContainer=document.createElement("div");
        this.nodeContainer.className="node conditionNode";
        this.context.container.appendChild(this.nodeContainer);
        this.initBindResizeEvent();
        var self=this;
        this.contentContainer=document.createElement("span");
        this.nodeContainer.appendChild(this.contentContainer);
        this.operator=new ruleforge.ComparisonOperator(function(){
            self.inputType=self.operator.getInputType();
            if(self.inputType){
                self.contentContainer.appendChild(self.inputType.getContainer());
            }
        });
        this.contentContainer.appendChild(this.operator.getContainer());

        var operations=document.createElement("span");
        operations.className="operations";
        operations.innerHTML="<i class='glyphicon glyphicon-ok-circle'></i>";
        this.nodeContainer.appendChild(operations);
        var menuItems=[];
        menuItems.push({
            name:"addCondition",
            label:"添加条件",
            onClick:function(){
                self.addChild("condition");
            }
        });
        menuItems.push({
            name:"addVariable",
            label:"添加变量",
            onClick:function(){
                self.addChild("variable");
            }
        });
        menuItems.push({
            name:"addAction",
            label:"添加动作",
            onClick:function(){
                self.addChild("action");
            }
        });
        menuItems.push({
            name:"delete",
            label:"删除",
            onClick:function(){
                MsgBox.confirm("真的要删除当前节点？",function(){
                    self.delete();
                });
            }
        });
        var menu=new RuleForge.menu.Menu({menuItems:menuItems});
        operations.addEventListener('click',function(e){
            menu.show(e);
        });
        this.nodeHeight=this.nodeContainer.offsetHeight+15;
        this.nodeWidth=this.nodeContainer.offsetWidth;
    }
    initData(data){
        if(!data){
            return;
        }
        var op=data["op"];
        this.operator.setOperator(op);
        var value=data["value"];
        this.operator.initRightValue(value);
        this.inputType=this.operator.getInputType();
        if(this.inputType){
            this.contentContainer.appendChild(this.inputType.getContainer());
        }
        super.initChildrenNodeData(data);
    }
    toXml(){
        if(this.children.length==0){
            throw "条件节点下至少要有一个动作节点.";
        }
        var xml="<condition-tree-node op=\""+this.operator.getOperator()+"\">";
        if(this.inputType){
            xml+=this.inputType.toXml();
        }
        this.children.forEach(function(connection) {
            xml+=connection.node.toXml();
        });
        xml+="</condition-tree-node>";
        return xml;
    }
};
