import BaseNode from './BaseNode.js';
/* bootbox is a global */

export default class ActionNode extends BaseNode{
    constructor(context,parentNode){
        super(context,parentNode);
        this.actionTypes=[];
        this.init();
    }
    init(){
        this.nodeContainer=document.createElement("div");
        this.nodeContainer.className="node actionNode";
        this.context.container.appendChild(this.nodeContainer);
        this.initBindResizeEvent();
        this.actionsContainer=document.createElement("span");
        this.actionsContainer.style.display="inline-block";
        this.nodeContainer.appendChild(this.actionsContainer);
        this.addAction();
        var self=this;
        var operations=document.createElement("span");
        operations.className="operations";
        operations.innerHTML="<i class='glyphicon glyphicon-ok-circle'></i>";
        this.nodeContainer.appendChild(operations);
        var menuItems=[];
        menuItems.push({
            name:"delete",
            label:"删除",
            onClick:function(){
                MsgBox.confirm("真的要删除当前节点？",function(){
                    self.delete();
                });
            }
        });
        menuItems.push({
            name:"addAction",
            label:"添加动作",
            onClick:function(){
                self.addAction(true);
            }
        });
        var menu=new RuleForge.menu.Menu({menuItems:menuItems});
        operations.addEventListener('click',function(e){
            menu.show(e);
        });
        this.nodeHeight=this.nodeContainer.offsetHeight+15;
        this.nodeWidth=this.nodeContainer.offsetWidth;
    }
    addAction(notfirst){
        var actionContainer=document.createElement("span");
        if(notfirst){
            actionContainer.style.display="block";
        }
        window._setDirty();
        var delIcon=document.createElement("i");
        delIcon.className="glyphicon glyphicon-minus-sign";
        delIcon.style.cssText="color: #ac2925;padding-right: 5px";
        actionContainer.appendChild(delIcon);
        this.actionsContainer.appendChild(actionContainer);
        var newActionType=new ruleforge.ActionType(actionContainer);
        this.actionTypes.push(newActionType);
        actionContainer.actionType=newActionType;
        var self=this;
        delIcon.addEventListener('click',function(){
            if(self.actionTypes.length===1){
                window.bootbox.alert("动作至少要有一个.");
                return;
            }
            var pos=-1;
            self.actionTypes.forEach(function(at) {
                if(at===actionContainer.actionType){
                    pos=i;
                    return false;
                }
            });
            if(pos!==-1){
                self.actionTypes.splice(pos,1);
                actionContainer.remove();
                window._setDirty();
            }else{
                window.bootbox.alert("未找到要删除的动作对象.");
            }
        });
        return newActionType;
    }
    initData(data){
        if(!data){
            return;
        }
        var actions=data["actions"];
        if(!actions || actions.length===0){
            return;
        }
        this.actionTypes[0].parentContainer.remove();
        this.actionTypes.splice(0,1);
        for(var i=0;i<actions.length;i++){
            var action=actions[i];
            var newActionType=this.addAction(i!==0);
            newActionType.initData(action);
        }
    }
    toXml(){
        var xml="<action-tree-node>";
        this.actionTypes.forEach(function(actionType) {
            xml+=actionType.toXml();
        });
        xml+="</action-tree-node>";
        return xml;
    }
};
