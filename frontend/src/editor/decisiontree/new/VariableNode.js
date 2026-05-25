/* bootbox is a global */
import BaseNode from './BaseNode.js';
export default class VariableNode extends BaseNode{
    constructor(context,parentNode,disabledDel){
        super(context,parentNode);
        this.disabledDel=disabledDel;
        this.init();
    }
    init(){
        this.nodeContainer=document.createElement("div");
        this.nodeContainer.className="node varNode";
        this.context.container.appendChild(this.nodeContainer);
        this.initBindResizeEvent();
        var contentContainer=document.createElement("span");
        this.nodeContainer.appendChild(contentContainer);
        this.condition=new ruleforge.ConditionLeft(contentContainer);
        var self=this;
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
        if(!this.disabledDel){
            menuItems.push({
                name:"delete",
                label:"删除",
                onClick:function(){
                    MsgBox.confirm("真的要删除当前节点？",function(){
                        self.delete();
                    });
                }
            });
        }
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
        var left=data["left"];
        this.condition.initData(left);
        super.initChildrenNodeData(data);
    }
    toXml(){
        if(this.children.length==0){
            throw "变量节点下至少要有一个条件节点.";
        }
        var xml="<variable-tree-node>";
        xml+=this.condition.toXml();
        this.children.forEach(function(connection) {
            xml+=connection.node.toXml();
        });
        xml+="</variable-tree-node>";
        return xml;
    }
};
