"use strict";(self["webpackChunkoci_help_web"]=self["webpackChunkoci_help_web"]||[]).push([[61],{7061:function(e,a,t){t.r(a),t.d(a,{default:function(){return k}});var l=t(6768),s=t(4232);const i={style:{"overflow-x":"auto"}},o={class:"dialog-footer"};function r(e,a,t,r,d,n){const c=(0,l.g2)("el-button"),u=(0,l.g2)("el-input"),g=(0,l.g2)("el-option"),p=(0,l.g2)("el-select"),h=(0,l.g2)("el-table-column"),m=(0,l.g2)("el-tooltip"),f=(0,l.g2)("el-tag"),b=(0,l.g2)("el-table"),k=(0,l.g2)("el-pagination"),C=(0,l.g2)("el-form-item"),F=(0,l.g2)("Key"),y=(0,l.g2)("el-icon"),_=(0,l.g2)("el-upload"),w=(0,l.g2)("el-form"),x=(0,l.g2)("el-tab-pane"),v=(0,l.g2)("upload-filled"),U=(0,l.g2)("el-tabs"),S=(0,l.g2)("el-dialog"),V=(0,l.gN)("loading");return(0,l.uX)(),(0,l.CE)(l.FK,null,[(0,l.Lk)("div",null,[(0,l.bF)(c,{type:"success",style:{"margin-bottom":"20px"},onClick:n.fetchUsers},{default:(0,l.k6)((()=>a[15]||(a[15]=[(0,l.eW)(" 刷新 ")]))),_:1},8,["onClick"]),(0,l.bF)(c,{type:"primary",onClick:a[0]||(a[0]=e=>d.dialogVisible=!0),style:{"margin-left":"20px","margin-bottom":"20px"}},{default:(0,l.k6)((()=>a[16]||(a[16]=[(0,l.eW)(" 新增配置 ")]))),_:1}),(0,l.bF)(u,{modelValue:d.pageParams.keyword,"onUpdate:modelValue":a[1]||(a[1]=e=>d.pageParams.keyword=e),placeholder:"模糊搜索名称/区域",onInput:n.fetchUsers,clearable:"","prefix-icon":"Search",style:{"margin-left":"20px",width:"200px","margin-bottom":"20px"}},null,8,["modelValue","onInput"]),(0,l.bF)(p,{modelValue:d.pageParams.isEnableCreate,"onUpdate:modelValue":a[2]||(a[2]=e=>d.pageParams.isEnableCreate=e),onChange:n.fetchUsers,placeholder:"开机任务状态筛选",style:{"margin-left":"20px",width:"180px","margin-bottom":"20px"}},{default:(0,l.k6)((()=>[(0,l.bF)(g,{label:"全部",value:null}),(0,l.bF)(g,{label:"仅显示执行开机任务中",value:1}),(0,l.bF)(g,{label:"无开机任务",value:0})])),_:1},8,["modelValue","onChange"]),(0,l.bF)(c,{type:"danger",onClick:n.showDeleteSelected,disabled:!d.selectedUsers.length,style:{"margin-left":"20px","margin-bottom":"20px"}},{default:(0,l.k6)((()=>a[17]||(a[17]=[(0,l.eW)(" 批量删除 ")]))),_:1},8,["onClick","disabled"]),(0,l.bF)(c,{type:"warning",onClick:n.createBatch,disabled:!d.selectedUsers.length,style:{"margin-left":"20px","margin-bottom":"20px"}},{default:(0,l.k6)((()=>a[18]||(a[18]=[(0,l.eW)(" 批量开机 ")]))),_:1},8,["onClick","disabled"]),(0,l.bF)(c,{type:"success",onClick:n.checkAlive,style:{"margin-left":"20px","margin-bottom":"20px"},loading:d.checkLoading},{default:(0,l.k6)((()=>a[19]||(a[19]=[(0,l.eW)(" 一键测活 ")]))),_:1},8,["onClick","loading"]),(0,l.Lk)("div",i,[(0,l.bo)(((0,l.uX)(),(0,l.Wv)(b,{data:d.users,style:{width:"100%","min-width":"800px"},onSelectionChange:n.handleSelectionChange},{default:(0,l.k6)((()=>[(0,l.bF)(h,{type:"selection",width:"55"}),(0,l.bF)(h,{label:"名称"},{default:(0,l.k6)((({row:e})=>[(0,l.bF)(m,{content:e.username,placement:"top"},{default:(0,l.k6)((()=>[(0,l.eW)((0,s.v_)(e.username),1)])),_:2},1032,["content"])])),_:1}),(0,l.bF)(h,{label:"租户名"},{default:(0,l.k6)((({row:e})=>[(0,l.bF)(f,{type:"success"},{default:(0,l.k6)((()=>[(0,l.eW)((0,s.v_)(e.tenantName),1)])),_:2},1024)])),_:1}),(0,l.bF)(h,{label:"区域"},{default:(0,l.k6)((({row:e})=>[(0,l.bF)(f,{type:"primary"},{default:(0,l.k6)((()=>[(0,l.eW)((0,s.v_)(e.region),1)])),_:2},1024)])),_:1}),(0,l.bF)(h,{label:"开机任务状态"},{default:(0,l.k6)((({row:e})=>[(0,l.bF)(f,{type:1===e.enableCreate?"success":"info"},{default:(0,l.k6)((()=>[(0,l.eW)((0,s.v_)(1===e.enableCreate?"执行开机任务中":"无开机任务"),1)])),_:2},1032,["type"])])),_:1}),(0,l.bF)(h,{sortable:"",prop:"createTime",label:"创建时间"},{default:(0,l.k6)((({row:e})=>[(0,l.bF)(f,{type:"info"},{default:(0,l.k6)((()=>[(0,l.eW)((0,s.v_)(e.createTime),1)])),_:2},1024)])),_:1}),(0,l.bF)(h,{fixed:"right",label:"操作",width:"300"},{default:(0,l.k6)((({row:e})=>[(0,l.bF)(c,{onClick:a=>n.viewDetails(e),type:"primary",link:""},{default:(0,l.k6)((()=>a[20]||(a[20]=[(0,l.eW)("详情")]))),_:2},1032,["onClick"]),(0,l.bF)(c,{onClick:a=>n.releaseSecurityRule(e),type:"success",link:""},{default:(0,l.k6)((()=>a[21]||(a[21]=[(0,l.eW)("安全列表放行")]))),_:2},1032,["onClick"]),(0,l.bF)(c,{onClick:a=>n.createInstance(e),type:"warning",link:""},{default:(0,l.k6)((()=>a[22]||(a[22]=[(0,l.eW)("开机")]))),_:2},1032,["onClick"]),(0,l.bF)(c,{onClick:a=>n.handleStopCreate(e),link:"",type:0===e.enableCreate?"info":"danger",disabled:0===e.enableCreate},{default:(0,l.k6)((()=>a[23]||(a[23]=[(0,l.eW)("停止开机 ")]))),_:2},1032,["onClick","type","disabled"])])),_:1})])),_:1},8,["data","onSelectionChange"])),[[V,d.loading]])]),(0,l.bF)(k,{onCurrentChange:n.handlePageChange,onSizeChange:n.handleSizeChange,"current-page":d.pageParams.currentPage,"onUpdate:currentPage":a[3]||(a[3]=e=>d.pageParams.currentPage=e),"page-size":d.pageParams.pageSize,"onUpdate:pageSize":a[4]||(a[4]=e=>d.pageParams.pageSize=e),"page-sizes":[5,10,20,50],total:d.total,layout:"total, sizes, prev, pager, next, jumper",style:{"margin-top":"20px"}},null,8,["onCurrentChange","onSizeChange","current-page","page-size","total"])]),(0,l.bF)(S,{modelValue:d.dialogVisible,"onUpdate:modelValue":a[10]||(a[10]=e=>d.dialogVisible=e),title:"新增配置",width:"50%",onClose:a[11]||(a[11]=e=>d.dialogVisible=!1)},{footer:(0,l.k6)((()=>[(0,l.bF)(c,{onClick:a[8]||(a[8]=e=>d.dialogVisible=!1)},{default:(0,l.k6)((()=>a[27]||(a[27]=[(0,l.eW)("取消")]))),_:1}),(0,l.bF)(c,{type:"primary",onClick:a[9]||(a[9]=e=>"manual"===d.activeTab?n.addUser():n.uploadFile()),disabled:("upload"!==d.activeTab||0===this.fileList.length)&&!d.addCfgFormData.disabled,loading:d.addLoading},{default:(0,l.k6)((()=>[(0,l.eW)((0,s.v_)("manual"===d.activeTab?"新增":"上传配置文件"),1)])),_:1},8,["disabled","loading"])])),default:(0,l.k6)((()=>[(0,l.bF)(U,{modelValue:d.activeTab,"onUpdate:modelValue":a[7]||(a[7]=e=>d.activeTab=e)},{default:(0,l.k6)((()=>[(0,l.bF)(x,{label:"手动输入",name:"manual"},{default:(0,l.k6)((()=>[(0,l.bF)(w,{ref:"formRef",model:d.addCfgFormData,rules:d.formRules,"label-width":"80px"},{default:(0,l.k6)((()=>[(0,l.bF)(C,{label:"配置名称",prop:"username"},{default:(0,l.k6)((()=>[(0,l.bF)(u,{modelValue:d.addCfgFormData.username,"onUpdate:modelValue":a[5]||(a[5]=e=>d.addCfgFormData.username=e),placeholder:"请输入自定义配置名称，例：这是我的首尔",clearable:""},null,8,["modelValue"])])),_:1}),(0,l.bF)(C,{label:"配置内容",prop:"ociCfgStr"},{default:(0,l.k6)((()=>[(0,l.bF)(u,{modelValue:d.addCfgFormData.ociCfgStr,"onUpdate:modelValue":a[6]||(a[6]=e=>d.addCfgFormData.ociCfgStr=e),type:"textarea",placeholder:d.placeholderText,rows:"6"},null,8,["modelValue","placeholder"])])),_:1}),(0,l.bF)(C,{label:"私钥文件",prop:"file"},{default:(0,l.k6)((()=>[(0,l.bF)(_,{drag:"","http-request":n.handleSingleFileUpload,style:{width:"100%"},"show-file-list":!0,"file-list":d.singleFileList},{default:(0,l.k6)((()=>[(0,l.bF)(y,null,{default:(0,l.k6)((()=>[(0,l.bF)(F)])),_:1}),a[24]||(a[24]=(0,l.Lk)("div",null,[(0,l.eW)(" 请上传私钥文件（xxx.pem） "),(0,l.Lk)("em",null,"点击选择文件或拖动文件到此处")],-1))])),_:1},8,["http-request","file-list"])])),_:1})])),_:1},8,["model","rules"])])),_:1}),(0,l.bF)(x,{label:"批量上传",name:"upload"},{default:(0,l.k6)((()=>[(0,l.bF)(_,{class:"upload-demo",drag:"","http-request":n.handleFileUpload,multiple:"","show-file-list":!0,"file-list":d.fileList},{tip:(0,l.k6)((()=>a[25]||(a[25]=[(0,l.Lk)("div",{class:"el-upload__tip"}," ⭐要求：文件格式为.ini或者.txt，每个API配置的第一行是你[自定义的配置名称]，例：[首尔01]。 私钥文件（.pem）需要先上传至/app/oci-helper/keys目录下，然后修改配置中的key_file=私钥文件名称.pem。 上传过程中会校验配置有效性，如不符合要求则无法添加成功~ ",-1)]))),default:(0,l.k6)((()=>[(0,l.bF)(y,{class:"el-icon--upload"},{default:(0,l.k6)((()=>[(0,l.bF)(v)])),_:1}),a[26]||(a[26]=(0,l.Lk)("div",{class:"el-upload__text"},[(0,l.eW)(" Drop file here or "),(0,l.Lk)("em",null,"click to upload")],-1))])),_:1},8,["http-request","file-list"])])),_:1})])),_:1},8,["modelValue"])])),_:1},8,["modelValue"]),(0,l.bF)(S,{modelValue:d.checkDialogVisible,"onUpdate:modelValue":a[13]||(a[13]=e=>d.checkDialogVisible=e),title:"测活结果",width:"35%",onClose:a[14]||(a[14]=e=>d.checkDialogVisible=!1)},{footer:(0,l.k6)((()=>[(0,l.Lk)("div",o,[(0,l.bF)(c,{type:"primary",onClick:a[12]||(a[12]=e=>d.checkDialogVisible=!1)},{default:(0,l.k6)((()=>a[28]||(a[28]=[(0,l.eW)(" 确定 ")]))),_:1})])])),default:(0,l.k6)((()=>[(0,l.Lk)("span",null,(0,s.v_)(this.checkResult),1)])),_:1},8,["modelValue"])],64)}t(4114),t(8992),t(3949),t(1454);var d=t(2565),n=t(7815),c=t(9623),u=t(47),g=t(1219),p=t(2933),h=t(7477),m={components:{ElTable:d.Up,ElTableColumn:d.o8,ElPagination:n.aQ,ElInput:c.WK,ElButton:u.S2,UploadFilled:h.UploadFilled},data(){return{checkLoading:!1,checkResult:"",users:[],pageParams:{keyword:"",currentPage:1,pageSize:5,isEnableCreate:null},total:2,selectedUsers:[],loading:!0,addLoading:!1,dialogVisible:!1,checkDialogVisible:!1,activeTab:"manual",fileList:[],singleFileList:[],addCfgFormData:{disabled:!1,username:"",ociCfgStr:"",file:null},formRules:{username:[{required:!0,message:"配置名称不能为空",trigger:"blur"},{min:1,message:"配置名称至少为 1 个字符",trigger:"blur"}],ociCfgStr:[{required:!0,message:"配置内容不能为空",trigger:"blur"},{min:10,message:"配置内容至少为 10 个字符",trigger:"blur"}],file:[{required:!0,message:"私钥文件不能为空",trigger:"change"}]},placeholderText:"user=ocid1.user.oc1..aaaaaaaaxxx\nfingerprint=c6:1b:9f:cd:01:9d:7a:xxx\ntenancy=ocid1.tenancy.oc1..aaaaaaaaxxx\nregion=sa-saopaulo-1\n"}},watch:{addCfgFormData:{handler(){this.validateForm()},deep:!0}},methods:{async fetchUsers(){this.loading=!0,this.$axios.post("/oci/userPage",{...this.pageParams}).then((e=>{this.users=e.data.data.records,this.total=e.data.data.total})).catch((e=>{console.error("Error:",e)})),this.loading=!1},async checkAlive(){this.checkLoading=!0,await this.$axios.post("/oci/checkAlive",{}).then((e=>{this.checkLoading=!1,e.data.success?(this.checkDialogVisible=!0,this.checkResult=e.data.msg):(0,g.nk)({message:e.data.msg,type:"error",duration:2e3}),this.fetchUsers()})).catch((e=>{console.error("Error:",e)}))},handlePageChange(e){this.pageParams.currentPage=e,this.fetchUsers()},handleSizeChange(e){this.pageParams.currentPage=1,this.pageParams.pageSize=e,this.fetchUsers()},handleSelectionChange(e){this.selectedUsers=e},handleSingleFileUpload({file:e}){this.singleFileList=[e],this.addCfgFormData.file=e},handleFileUpload({file:e}){const a=new FileReader;a.onload=a=>{const t=a.target.result;t&&this.fileList.push(e)},a.readAsArrayBuffer(e)},async uploadFile(){this.addLoading=!0;const e=new FormData;this.fileList.forEach((a=>{e.append("fileList",a)})),await this.$axios.post("/oci/uploadCfg",e,{headers:{"Content-Type":"multipart/form-data"}}).then((e=>{e.data.success?(this.resetForm(),(0,g.nk)({message:e.data.msg,type:"success",duration:2e3})):(this.addLoading=!1,(0,g.nk)({message:e.data.msg,type:"error",duration:2e3})),this.fetchUsers()})).catch((e=>{console.error("Error:",e)})),this.resetForm()},resetForm(){this.addCfgFormData={disabled:!1,username:"",ociCfgStr:""},this.fileList=[],this.addLoading=!1,this.dialogVisible=!1},async addUser(){if(0===this.singleFileList.length)return void(0,g.nk)({message:"请先上传私钥文件",type:"warning",duration:2e3});const e=new FormData;e.append("username",this.addCfgFormData.username),e.append("ociCfgStr",this.addCfgFormData.ociCfgStr),e.append("file",this.addCfgFormData.file),this.addLoading=!0,await this.$axios.post("/oci/addCfg",e,{headers:{"Content-Type":"multipart/form-data"}}).then((e=>{e.data.success?(this.resetForm(),(0,g.nk)({message:e.data.msg,type:"success",duration:2e3})):(this.addLoading=!1,(0,g.nk)({message:e.data.msg,type:"error",duration:2e3})),this.fetchUsers()})).catch((e=>{console.error("Error:",e)}))},viewDetails(e){this.$router.push({path:"/dashboard/OciCfgTab",query:{cfgId:e.id,username:e.username}})},createInstance(e){this.$router.push({path:"/dashboard/createInstance",query:{row:JSON.stringify(e)}})},createBatch(){this.$router.push({path:"/dashboard/ociCreateInstanceBatch",query:{row:JSON.stringify(this.selectedUsers)}})},async releaseSecurityRule(e){try{const a=await p.s.confirm("确定要放行默认 VCN 安全列表的所有端口及协议吗？","安全列表放行操作",{confirmButtonText:"确定",cancelButtonText:"取消",type:"warning"});"confirm"===a&&await this.$axios.post("/oci/releaseSecurityRule",{ociCfgId:e.id}).then((e=>{e.data.success?(0,g.nk)({message:e.data.msg,type:"success",duration:2e3}):(0,g.nk)({message:e.data.msg,type:"error",duration:2e3}),this.fetchUsers()})).catch((e=>{console.error("Error:",e)}))}catch(a){(0,g.nk)({message:"操作已取消",type:"info",duration:2e3})}},handleStopCreate(e){const a=window.confirm("确定要停止开机任务吗?");a?this.stopCreate(e):this.$message.info("已取消")},stopCreate(e){this.$axios.post("/oci/stopCreate",{userId:e.id}).then((e=>{e.data.success?(0,g.nk)({message:e.data.msg,type:"success",duration:2e3}):(0,g.nk)({message:e.data.msg,type:"error",duration:2e3}),this.fetchUsers()})).catch((e=>{console.error("Error:",e)}))},showDeleteSelected(){const e=window.confirm("确定要删除这些配置吗?");e?this.deleteSelected():this.$message.info("取消删除")},deleteSelected(){const e=this.selectedUsers.map((e=>e.id));this.$axios.post("/oci/removeCfg",{idList:e}).then((e=>{e.data.success?(0,g.nk)({message:e.data.msg,type:"success",duration:2e3}):(0,g.nk)({message:e.data.msg,type:"error",duration:2e3}),this.fetchUsers()})).catch((e=>{console.error("Error:",e)}))},validateForm(){this.$refs.formRef.validate((e=>{this.addCfgFormData.disabled=e}))}},mounted(){this.fetchUsers()}},f=t(1241);const b=(0,f.A)(m,[["render",r],["__scopeId","data-v-313029dc"]]);var k=b}}]);
//# sourceMappingURL=61.4d57f3fc.js.map