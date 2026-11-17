-- 中文说明：下方每组先记录原工作表及行号，再以范围 ID 仅补缺插入；SQL 有效语句须与生成器一致。
-- 由批准的操作范围清单生成，仅包含 243 项目录种子。
-- 执行前审核审批，不编造 NC 功能节点或按钮编码。
-- 保留已存在行；新条目保持未映射且未验证。
-- 不显式提交，不启用或修改任何已有策略。
-- 来源工作表及行号：Sheet2 第 2 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B9C29A5B958696F4' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户基本分类','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 3 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-0DA86324DBBF4DE6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户基本分类','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 4 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E332E41B042F9540' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户基本分类','停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 5 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-9F902C76B60FBE04' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户基本分类','启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 6 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E576276F26109C16' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 7 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-35B5B1E09B5A4A80' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 8 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E9B352C5EACB0820' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 9 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-0245CC5E8FA59478' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 10 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-56EC7D5EE24A0804' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','批改-快速批改','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 11 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-A9C10437DFDE8814' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','分配-分配','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 12 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8555A9BFE51EA438' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','取消分配-快速取消分配','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 13 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-7B87FD76F6ADBDC0' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','冻结','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 14 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B856D13140D5457B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','解冻','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 15 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E153DD629D00947F' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','银行账户-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 16 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-70EC3A5C19555BD7' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','银行账户-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 17 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-7493FA0F20E75AF0' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','银行账户-停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 18 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-DDC46B3D9F641DFF' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','银行账户-启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 19 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-36702F5EC874AF77' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','财务信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 20 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-6FCD3C3549E70950' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','财务信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 21 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-56F5E6C99AD9B4B7' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','销售信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 22 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-D1DA60E564CAF142' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','销售信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 23 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-A46C77B59E5AD49E' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','信用控制信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 24 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-AA8986FF31D0B647' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','信用控制信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 25 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2D9AA713970C7E86' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商基本分类','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 26 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-0BB4B107C5D6B51F' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商基本分类','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 27 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-D9F13F397DF1DF0B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商基本分类','停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 28 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-93CFA69510E56403' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商基本分类','启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 29 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E8C69ACAF76ECABF' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 30 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-83A95678666FC134' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 31 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F6D1E9BB792F6075' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 32 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-D9EF39B9A666E88D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 33 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-122DB04892CFDA8B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','批改-快速批改','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 34 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-FD8282A38B9C4E89' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','分配-分配','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 35 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-309E9ED628D45CDF' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','取消分配-快速取消分配','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 36 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-1EFAFB0888F5A7CA' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','冻结','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 37 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E2F3A16029CD1BC1' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','解冻','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 38 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E70DEE0145D79FDE' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','银行账户-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 39 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-00DF272BF31F0AA2' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','银行账户-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 40 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-24C25407F1C36AF6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','银行账户-停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 41 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B3A292BB1D2F3E84' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','银行账户-启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 42 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C9ED571D3AB7DD28' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','财务信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 43 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-643C1BCD6537DD6B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','财务信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 44 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8D2A39DEDA2F588D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','采购信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 45 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F136737476331BC2' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','采购信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 46 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-5D9816B2EE6203BC' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料基本分类','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 47 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-689A1D26EF57426B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料基本分类','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 48 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8C9F0ACFE6E32A2B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料基本分类','停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 49 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-42DE49AC3986C6B1' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料基本分类','启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 50 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-A7FBCA61C6991D48' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 51 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2F2608B07F6B73DD' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 52 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-4D029F8FC9693DD9' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 53 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-93014EDACA48E4D1' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 54 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-4CE7157ECDB4A443' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','批改-快速批改','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 55 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-755F2D0C43B0646E' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','分配-分配','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 56 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-CE419A6EAAED8307' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','取消分配-快速取消分配','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 57 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B48245BDDB687314' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','财务信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 58 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-FA635534B542BA71' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','财务信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 59 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2DAE0B30BE8A146B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','采购信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 60 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2B20B2A6925C5B1A' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','采购信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 61 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E5FA2B4E1183BF51' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','销售信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 62 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-4DA3476F6CC93426' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','销售信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 63 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-92F433355082640B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','库存信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 64 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2148AD881831CEFA' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','库存信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 65 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B54294B82124C93D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','计划信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 66 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-AF7F85BA60C742D9' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','计划信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 67 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-765869462A8F9898' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','生成信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 68 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-DB52192A0DE19F91' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','生成信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 69 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-98DD9EFAD6E1C54E' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','成本信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 70 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E8808CF0B60EED9C' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','成本信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 71 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-00D8C4530BE5A4C4' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'仓库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 72 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-25C42CF52E33610D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'仓库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 73 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-22C93F5AF42F721D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'仓库','停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 74 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B9B8E830DE3C5B6D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'仓库','启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 75 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-5BD5E8E063ECF621' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'货位','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 76 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-62A408A680D6A829' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'货位','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 77 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-69884D289EB5AFCC' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'货位','停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 78 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8B947039D517EEF6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'货位','启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 79 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-01D5908833FEB210' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 80 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-985FB7742F2A5B92' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 81 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B840FE4C55926A43' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 82 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-441DAD9CFDDEF982' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 83 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-63E91AC6F7B692B3' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 84 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C3E8EDB0010ADE64' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 85 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-205F657A98383B3C' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','辅助功能-整单关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 86 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-FCF630A4D7ABCB0E' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','辅助功能-整单打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 87 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-88AD4ED26D06F0CD' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','辅助功能-行关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 88 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-92362BAAB3F1F75E' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','辅助功能-行打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 89 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-490EA13F8CCF818D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 90 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-6CE160397F888C22' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 91 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-4FEB4DC30CF718EC' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 92 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-D0431E7A49F7EECF' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 93 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-39FAB956E796E631' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 94 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8C3548A53305EFF6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 95 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-92837F547CF79D01' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','辅助功能-整单关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 96 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-42210241A288DA84' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','辅助功能-整单打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 97 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-1143845FE4B694C7' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','辅助功能-行关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 98 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-1B6844EFCAE114B1' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','辅助功能-行打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 99 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-700B183E3B829BE5' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 100 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-496383D5683CD177' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 101 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-156925368009D472' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 102 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F9BFB7B253EDC029' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 103 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-7E89B6942DBFFA41' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 104 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-51D6153BAF1913D8' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 105 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-764897D0C3CDC231' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','辅助功能-冻结','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 106 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F284551644C877AD' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','辅助功能-解冻','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 107 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-5F83691030EF585B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','辅助功能-整单关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 108 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-07F548767DF29BC8' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','辅助功能-整单打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 109 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-60D1EAE5C335DBCB' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','辅助功能-行关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 110 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-D46BE2973050B272' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','辅助功能-行打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 111 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-A2C3C7E7BD8DA3A8' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 112 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-25794507518C73A9' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 113 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-BEEBC60CC1F9F64C' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 114 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-398D18AE89E2C083' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 115 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E3FB97ABA534CDC7' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 116 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-D5A17A54EEFD1E8D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 117 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-81027D3704101930' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','辅助功能-冻结','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 118 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-0F907C5F9342D2A7' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','辅助功能-解冻','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 119 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-63036038836AE4FE' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','关联发票-确认','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 120 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-7130EDE50CABE8FE' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','取消关联发票','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 121 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-94AFEFCA6F5958DA' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','关联功能-传应付','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 122 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-1652A63F9BC99CC3' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','关联功能-取消传应付','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 123 行，分类： 采购价格
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8FDD3381BD545EED' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商价目表维护','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 124 行，分类： 采购价格
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-3C98471632CF0434' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商价目表维护','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 125 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F7DBD94ACB7D9546' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'期初余额','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 126 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2A81D447CED47CF6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'期初余额','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 127 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C10800F14217C2EA' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'期初余额','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 128 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-AAFA9227A1F96898' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'期初余额','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 129 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-43CFB46A5D6F614C' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购入库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 130 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-BBB6F605E37071ED' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购入库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 131 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-3DD170546891640A' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购入库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 132 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-888EEE9C09C7EA2E' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购入库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 133 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C97F7D23C57508FB' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'投产单','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 134 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2E5602AD2FF78DCA' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'投产单','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 135 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-EB68D7B4AED73E99' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'投产单','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 136 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-646556AA195B8A48' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'投产单','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 137 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-1B5D1E16EF083AD2' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'调拨入库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 138 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2E25582C897E1BD5' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'调拨入库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 139 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-AF5A7A4A15748875' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'调拨入库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 140 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8F2B5546A8578867' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'调拨入库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 141 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2C30480472C0E461' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'委外加工入库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 142 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-87BCC12A32FE28F8' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'委外加工入库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 143 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-97BDCF1EFAE8DFF9' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'委外加工入库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 144 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-643237D91DBA2D52' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'委外加工入库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 145 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F76979AADB8E2E71' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'产成品入库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 146 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C05A0CA556EED8E7' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'产成品入库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 147 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2AAA91681E6A0692' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'产成品入库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 148 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-624D28082F8424E8' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'产成品入库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 149 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-EEA7B163450FFE7B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'其他入库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 150 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-1D188A6A017BF122' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'其他入库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 151 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-17289E7A161A00E7' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'其他入库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 152 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-7375F62EF78833C6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'其他入库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 153 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-91536308A44A2EEC' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'委托加工入库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 154 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8D0A4945C08F4EC8' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'委托加工入库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 155 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-EF0C02845E215DDE' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'委托加工入库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 156 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-88A31272A089EA2D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'委托加工入库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 157 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-41CC70D7534AF3B1' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'出库申请单','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 158 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C31C9590475F4297' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'出库申请单','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 159 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-931D24D0DD84834A' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'出库申请单','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 160 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-6D9C72222CF4B33C' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'出库申请单','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 161 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C7EA33244DC87A40' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'出库申请单','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 162 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-97A9C5615397566C' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'出库申请单','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 163 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-92D15BDDBB698776' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'出库申请单','行关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 164 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E8E246982D39D8AD' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'出库申请单','行打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 165 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-11F0F608424EC25A' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售出库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 166 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-41800183BD27AB33' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售出库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 167 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-A3F1772B910FC6A8' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售出库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 168 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-29F2810C210A1B58' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售出库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 169 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8EAA046EE4BC2096' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'调拨出库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 170 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-A38A5E0163F76F5A' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'调拨出库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 171 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8767AE5097F761E6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'调拨出库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 172 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-DC0D566DC68C848E' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'调拨出库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 173 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C34C17D921A22959' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'材料出库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 174 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-7A912AE266075A13' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'材料出库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 175 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B6AC766DEB957C2A' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'材料出库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 176 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-3EE79F6544A4BE12' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'材料出库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 177 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-6A609F1B56ADF143' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'其他出库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 178 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-A2EFF9E33873AB4F' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'其他出库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 179 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8E55706979387FAB' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'其他出库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 180 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-1AF23ED29EF95F14' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'其他出库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 181 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B56EB0514CBEE93F' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'盘点','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 182 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-143AF63950B3FA7C' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'盘点','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 183 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-163569189B7E0E2D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'盘点','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 184 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C23E3866BABAA0F5' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'盘点','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 185 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-549D8CA6BFE533A9' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'盘点','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 186 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8CEAEBD876F03C72' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'盘点','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 187 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-817766AFE162CF43' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'盘点','调整-保存&签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 188 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-38F4DC644D8F263D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'盘点','取消调整','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 189 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-FC88F9EEFF039FDC' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 190 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8C642A3CAE8C797F' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 191 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-AB053148A6BF31F1' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 192 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E31D2EFC02DBA25A' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 193 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-240C2DCFECE6E17E' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 194 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-339A6950CDA25292' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 195 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8DAFD17383D827EA' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','直接转出-保存&签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 196 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8103F7CD6D4B4180' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','转出-保存&签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 197 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B62E3522D6CAD698' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','转入-保存&签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 198 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F33D5BEE0B239BAF' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'货位调整','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 199 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-195766C97B0515E3' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'货位调整','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 200 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F3256F1FE0D536F5' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'形态转换','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 201 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-286EB541FD910A2F' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'形态转换','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 202 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-33E54B1A6F7F8BB3' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'形态转换','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 203 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-4195634597158FF5' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'形态转换','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 204 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2101228438A33DAC' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'形态转换','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 205 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-ADC1AF5474A17427' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'形态转换','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 206 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-96F6A5E398D5E2F7' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'形态转换','生成出入库单-保存&签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 207 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B3F539FB0440DD14' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'库存状态调整','状态调整-确认','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 208 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-EAF88AC5CAF75442' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 209 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-FEFBC5B292C330AD' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 210 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-4671CC2E70A34B97' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 211 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-4F23D38FB22BA45F' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 212 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-9C0B035FFEB2110D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 213 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-51BB76212FFCD156' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 214 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C7E51B3ED69F8B05' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','辅助功能-冻结','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 215 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-BF1F44B944C037B3' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','辅助功能-解冻','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 216 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2A439AB1FE3628B0' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','辅助功能-整单关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 217 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-6DA27F33C45CD3B0' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','整单关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 218 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-4A1493B793E22F6D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','整单打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 219 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F598A0B4A16EFE84' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','行关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 220 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-FF713F12789B45A1' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','行打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 221 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-887831FDC8C23F76' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','发货关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 222 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8A7BC4FA7E1631FB' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','发货打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 223 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-32542641C9DC8398' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','出库关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 224 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-64B839C61D768D19' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','出库打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 225 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-9985A340EB7EFFC4' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','开票关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 226 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C6E139BBEF4B8BE6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','开票打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 227 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-BA71E137623CEC43' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','结算关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 228 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-42EE6E523F56E9D0' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','结算打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 229 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-04ECAF336F8932CA' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 230 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-FF81305763F3CF1F' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 231 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-D494A8A893DDBC0B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 232 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-14232F4638F48C8C' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 233 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-257B45A6A4F80EF6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 234 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-7DA00D0A9032E168' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 235 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-52A72C09C2BE1F47' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','辅助功能-整单关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 236 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-61C46C0C9ED0BC7D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','辅助功能-整单打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 237 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-AF1994D66608BEF1' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','辅助功能-出库关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 238 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-5275CF042A861E3D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','辅助功能-出库打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 239 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-176DC431DD4F5803' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售发票维护','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 240 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8F8846F2D6E1ECD0' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售发票维护','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 241 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-56499B75D3E91C33' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售发票维护','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 242 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-29895E169F92EB91' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售发票维护','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 243 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-9AE255EF674BD8E2' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售发票维护','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 244 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-5EBFFEF0FFE24CAA' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售发票维护','取消审批','UNMAPPED','N');
