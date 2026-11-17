-- Read-only site discovery for customer, supplier and material-class candidates.
-- This script does not enable routes, create objects, or update NC data.
SELECT SYS_CONTEXT('USERENV','SESSION_USER') AS session_user,
       SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AS current_schema,
       SYS_CONTEXT('USERENV','DB_UNIQUE_NAME') AS database_name,
       LOWER(SYS_CONTEXT('USERENV','SERVER_HOST')) AS server_host FROM dual;

SELECT f.funcode,p.paramname,p.paramvalue
  FROM sm_funcregister f JOIN sm_paramregister p ON p.parentid=f.cfunid
 WHERE f.funcode IN ('10140CUG','10140CUO','10140SUG','10140SUO','10140MCLG','10140MCLO')
   AND p.paramname='BeanConfigFilePath' AND NVL(f.dr,0)=0 AND NVL(p.dr,0)=0
 ORDER BY f.funcode,p.paramname;

SELECT metadataid,nodekey,exbean_name,ex_configpath
  FROM bd_uitabextend
 WHERE metadataid IN ('10140CUG','10140CUO','10140SUG','10140SUO','10140MCLG','10140MCLO',
                      'e4f48eaf-5567-4383-a370-a59cb3e8a451',
                      '720dcc7c-ff19-48f4-b9c5-b90906682f45')
 ORDER BY metadataid,nodekey,exbean_name;

SELECT f.funcode,b.btncode,b.pk_btn
  FROM sm_funcregister f JOIN sm_butnregister b ON (b.parent_id=f.cfunid OR b.parent_id=f.funcode)
 WHERE f.funcode IN ('10140CUG','10140CUO','10140SUG','10140SUO','10140MCLG','10140MCLO')
   AND b.btncode IN ('Save','Delete','Assign','CancelAssign','BatchUpdate')
   AND NVL(f.dr,0)=0 AND NVL(b.dr,0)=0
 ORDER BY f.funcode,b.btncode;

SELECT table_name,column_name,data_type,data_length,nullable
  FROM user_tab_columns
 WHERE table_name IN ('BD_CUSTOMER','BD_SUPPLIER','BD_CUST_SUPPLIER','BD_CUSTSTRUCTURE',
                      'BD_SUPSTRUCTURE','BD_MARBASCLASS','BD_UITABEXTEND')
 ORDER BY table_name,column_id;

SELECT table_name,trigger_name,status,triggering_event
  FROM user_triggers
 WHERE table_name IN ('BD_CUSTOMER','BD_SUPPLIER','BD_CUST_SUPPLIER','BD_CUSTSTRUCTURE',
                      'BD_SUPSTRUCTURE','BD_MARBASCLASS')
 ORDER BY table_name,trigger_name;
