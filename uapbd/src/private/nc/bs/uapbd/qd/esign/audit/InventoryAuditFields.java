package nc.bs.uapbd.qd.esign.audit;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Business columns from NC inventory metadata and the verified QDNC schema snapshot. */
final class InventoryAuditFields {
    private InventoryAuditFields() { }

    private static final Set<String> IC_PURCHASEIN_H = fields(
            "approver bc_03 billmaker bitinbill btriatradeflag cbizid cbiztype ccostdomainid ccustomerid cdptid " +
            "cdptvid cfanaceorgoid cfanaceorgvid cgeneralhid corpoid corpvid cpayfinorgoid cpayfinorgvid " +
            "cpurorgoid cpurorgvid creationtime creator crececountryid csendcountryid csendtypeid ctaxcountryid " +
            "ctradewordid ctrantypeid cvendorid cwarehouseid cwhsmanagerid dbilldate dmakedate dr fbillflag " +
            "fbuysellflag freplenishflag iprintcount modifiedtime modifier ntotalnum ntotalpiece ntotalvolume " +
            "ntotalweight pk_group pk_measware pk_org pk_org_v taudittime ts vbillcode vdef1 vdef10 vdef11 vdef12 " +
            "vdef13 vdef14 vdef15 vdef16 vdef17 vdef18 vdef19 vdef2 vdef20 vdef3 vdef4 vdef5 vdef6 vdef7 vdef8 " +
            "vdef9 vnote vreturnreason vtrantypecode");

    private static final Set<String> IC_PURCHASEIN_B = fields(
            "bassetcard bbarcodeclose bc_04 bfixedasset bonroadflag bopptaxflag borrowinflag bsourcelargess " +
            "carriveorder_bbid casscustid castunitid cbodytranstypecode cbodywarehouseid ccheckstateid " +
            "ccorrespondbid ccorrespondcode ccorrespondhid ccorrespondrowno ccorrespondtranstype ccorrespondtype " +
            "ccurrencyid cdestiareaid cdesticountryid cdetailbid cdetailid cdetailrowno cetdetlpickbid " +
            "cfanaceorgoid cffileid cfirstbillbid cfirstbillhid cfirsttranstype cfirsttype cgeneralbid " +
            "cgeneralhid cglobalcurrencyid cgroupcurrencyid cioliabilityoid cioliabilityvid cliabilityoid " +
            "cliabilityvid clocationid cmaterialoid cmaterialvid corder_bb1id corigareaid corigcountryid " +
            "corigcurrencyid corpoid corpvid cproductorid cprojectid cprojecttaskid cqtunitid cqualitylevelid " +
            "creqstoorgoid creqstoorgvid crowno cselastunitid csettlecurrencyid csnqualitylevelid csnunitid " +
            "csourcebillbid csourcebillhid csourcetranstype csourcetype csrc2billbid csrc2billhid csrc2billtype " +
            "csrc2transtype csrcmaterialoid csrcmaterialvid cstateid ctaxcodeid ctplcustomerid cunitid cvendorid " +
            "cvmivenderid dbizdate dinbounddate dproducedate dr dvalidate fbillrowflag fchecked flargess " +
            "ftaxtypeflag ibcversion idesatype naccumsettlenum naccumtonum naccumvminum nassistnum nbarcodenum " +
            "ncalcostmny ncaltaxmny ncanreplnum nchangestdrate ncorrespondastnum ncorrespondgrsnum ncorrespondnum " +
            "ncountnum nglobalexchgrate nglobalmny nglobaltaxmny ngrossnum ngroupexchgrate ngroupmny ngrouptaxmny " +
            "ninvoicemny ninvoicenum nitemdiscountrate nkdnum nmny nnetprice nnosubtax nnosubtaxrate nnum " +
            "norigmny norignetprice norigprice norigtaxmny norigtaxnetprice norigtaxprice npickupnum npiece " +
            "nplannedmny nplannedprice nprice nqtnetprice nqtorignetprice nqtorigprice nqtorigtaxnetprice " +
            "nqtorigtaxprice nqtprice nqttaxnetprice nqttaxprice nqtunitnum nreplenishedastnum nreplenishednum " +
            "nsettleprice nsettlepricenotax nshouldassistnum nshouldnum nsignnum ntarenum ntax ntaxmny " +
            "ntaxnetprice ntaxprice ntaxrate ntotaltransnum ntotaltransportnum nvolume nweight pk_batchcode " +
            "pk_creqwareid pk_group pk_measware pk_org pk_org_v pk_packsort pk_serialcode tchecktime ts " +
            "tsourcebodyts tsourceheadts vbatchcode vbatchcodenote vbcdef1 vbcdef10 vbcdef11 vbcdef12 vbcdef13 " +
            "vbcdef14 vbcdef15 vbcdef16 vbcdef17 vbcdef18 vbcdef19 vbcdef2 vbcdef20 vbcdef3 vbcdef4 vbcdef5 " +
            "vbcdef6 vbcdef7 vbcdef8 vbcdef9 vbdef1 vbdef10 vbdef11 vbdef12 vbdef13 vbdef14 vbdef15 vbdef16 " +
            "vbdef17 vbdef18 vbdef19 vbdef2 vbdef20 vbdef3 vbdef4 vbdef5 vbdef6 vbdef7 vbdef8 vbdef9 vbillbarcode " +
            "vchangerate vdetailbillcode vexigencybid vexigencycode vexigencyhid vexigencyrowno vexigencytype " +
            "vfirstbillcode vfirstrowno vfree1 vfree10 vfree2 vfree3 vfree4 vfree5 vfree6 vfree7 vfree8 vfree9 " +
            "vitcontractbillcode vnotebody vqtunitrate vreturnreason vserialcode vsndef1 vsndef10 vsndef11 " +
            "vsndef12 vsndef13 vsndef14 vsndef15 vsndef16 vsndef17 vsndef18 vsndef19 vsndef2 vsndef20 vsndef21 " +
            "vsndef22 vsndef23 vsndef24 vsndef25 vsndef26 vsndef27 vsndef28 vsndef29 vsndef3 vsndef30 vsndef31 " +
            "vsndef32 vsndef33 vsndef34 vsndef35 vsndef36 vsndef37 vsndef38 vsndef39 vsndef4 vsndef40 vsndef5 " +
            "vsndef6 vsndef7 vsndef8 vsndef9 vsourcebillcode vsourcerowno vsrc2billcode vsrc2billrowno " +
            "vvendbatchcode");

    private static final Set<String> IC_PURCHASEIN_L = fields(
            "cgeneralbid cgeneralhid cgenerallid clocationid corpoid corpvid csnqualitylevelid csnunitid dr " +
            "nassistnum ngrossnum nnum nshouldassistnum nshouldnum pk_group pk_serialcode ts vbarcode vbarcodesub " +
            "vboxbarcode vserialcode vsndef1 vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 vsndef15 vsndef16 " +
            "vsndef17 vsndef18 vsndef19 vsndef2 vsndef20 vsndef21 vsndef22 vsndef23 vsndef24 vsndef25 vsndef26 " +
            "vsndef27 vsndef28 vsndef29 vsndef3 vsndef30 vsndef31 vsndef32 vsndef33 vsndef34 vsndef35 vsndef36 " +
            "vsndef37 vsndef38 vsndef39 vsndef4 vsndef40 vsndef5 vsndef6 vsndef7 vsndef8 vsndef9");

    private static final Set<String> IC_SALEOUT_H = fields(
            "approver bc_03 billmaker boutretflag bsalecooppur btriatradeflag cbizid cbiztype ccostdomainid " +
            "ccustomerid cdilivertypeid cdptid cdptvid cfanaceorgoid cfanaceorgvid cgeneralhid corpoid corpvid " +
            "cothercalbodyoid cothercalbodyvid cotherwhid creationtime creator crececountryid creceivfinorgoid " +
            "creceivfinorgvid csaleorgoid csaleorgvid csendcountryid ctaxcountryid ctradewordid ctrancustid " +
            "ctrantypeid cwarehouseid cwhsmanagerid dbilldate dmakedate dr fbillflag fbuysellflag freplenishflag " +
            "iprintcount modifiedtime modifier ntotalnum ntotalpiece ntotalvolume ntotalweight pk_group " +
            "pk_measware pk_org pk_org_v taudittime trafficorgoid trafficorgvid ts vbillcode vdef1 vdef10 vdef11 " +
            "vdef12 vdef13 vdef14 vdef15 vdef16 vdef17 vdef18 vdef19 vdef2 vdef20 vdef3 vdef4 vdef5 vdef6 vdef7 " +
            "vdef8 vdef9 vdiliveraddress vnote vtrantypecode");

    private static final Set<String> IC_SALEOUT_B = fields(
            "badvfeeflag bbarcodeclose bc_04 bcloseord binvoiceflag blendoutflag bonroadflag breturnprofit " +
            "bsafeprice bsample bsourcelargess btou8rm btranendflag bwastageflag casscustid castunitid " +
            "cbodytranstypecode cbodywarehouseid ccorrespondbid ccorrespondcode ccorrespondhid ccorrespondrowno " +
            "ccorrespondtranstype ccorrespondtype ccostobject ccurrencyid ccustmaterialid cdelivery_bbid " +
            "cfanaceorgoid cffileid cfirstbillbid cfirstbilldate cfirstbillhid cfirsttranstype cfirsttype " +
            "cgeneralbid cgeneralhid cglobalcurrencyid cgroupcurrencyid cinvoicecustid cioliabilityoid " +
            "cioliabilityvid cliabilityoid cliabilityvid clocationid cmaterialoid cmaterialvid corigareaid " +
            "corigcountryid corigcurrencyid corpoid corpvid cprodlineid cproductorid cprojectid cqtunitid " +
            "cqualitylevelid creceieveid creceiveareaid creceivepointid crowno cselastunitid csignwastbid " +
            "csignwasthid csignwasttype csnqualitylevelid csnunitid csourcebillbid csourcebilldate csourcebillhid " +
            "csourcematerialoid csourcetranstype csourcetype csourcewastbid csourcewasthid csourcewasttranstype " +
            "csourcewasttype csrc2billbid csrc2billhid csrc2billtype csrc2transtype csrcmaterialoid " +
            "csrcmaterialvid cstateid csumid ctaxcodeid ctplcustomerid cunitid cvendorid cvmivenderid dbizdate " +
            "ddeliverdate dinbounddate dproducedate dr dvalidate fbillrowflag flargess ftaxtypeflag ibcversion " +
            "idesatype naccumoutbacknum naccumoutsignnum naccumvminum naccumwastnum nassistnum nbarcodenum " +
            "nbdiscountrate ncaltaxmny nchangestdrate ncorrespondastnum ncorrespondgrsnum ncorrespondnum ncostmny " +
            "ncostprice ncountnum nglobalexchgrate nglobalmny nglobaltaxmny ngrossnum ngroupexchgrate ngroupmny " +
            "ngrouptaxmny ninvoicemny ninvoicenum nitemdiscountrate nmny nnetprice nnum norigmny norignetprice " +
            "norigprice norigtax norigtaxmny norigtaxnetprice norigtaxprice npiece nplannedmny nplannedprice " +
            "nprice nqtnetprice nqtorignetprice nqtorigprice nqtorigtaxnetprc nqtorigtaxnetprice nqtorigtaxprice " +
            "nqtprice nqttaxnetprice nqttaxprice nqtunitnum nreplenishednum nrushnum nshouldassistnum nshouldnum " +
            "nsignnum ntarenum ntax ntaxmny ntaxnetprice ntaxprice ntaxrate ntotaltrannum nvolume nweight " +
            "pk_batchcode pk_group pk_measware pk_org pk_org_v pk_packsort pk_returnreason pk_serialcode " +
            "tchecktime ts tsourcebbts tsourcebodyts tsourceheadts vbatchcode vbatchcodenote vbcdef1 vbcdef10 " +
            "vbcdef11 vbcdef12 vbcdef13 vbcdef14 vbcdef15 vbcdef16 vbcdef17 vbcdef18 vbcdef19 vbcdef2 vbcdef20 " +
            "vbcdef3 vbcdef4 vbcdef5 vbcdef6 vbcdef7 vbcdef8 vbcdef9 vbdef1 vbdef10 vbdef11 vbdef12 vbdef13 " +
            "vbdef14 vbdef15 vbdef16 vbdef17 vbdef18 vbdef19 vbdef2 vbdef20 vbdef3 vbdef4 vbdef5 vbdef6 vbdef7 " +
            "vbdef8 vbdef9 vbillbarcode vbilltypeu8rm vchangerate vexigencybid vexigencycode vexigencyhid " +
            "vexigencyrowno vexigencytype vfirstbillcode vfirstrowno vfree1 vfree10 vfree2 vfree3 vfree4 vfree5 " +
            "vfree6 vfree7 vfree8 vfree9 vnotebody vpickdetailbid vpickdetailrowno vqtunitrate vreceiveaddress " +
            "vserialcode vsignwastcode vsignwastrowno vsndef1 vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 " +
            "vsndef15 vsndef16 vsndef17 vsndef18 vsndef19 vsndef2 vsndef20 vsndef21 vsndef22 vsndef23 vsndef24 " +
            "vsndef25 vsndef26 vsndef27 vsndef28 vsndef29 vsndef3 vsndef30 vsndef31 vsndef32 vsndef33 vsndef34 " +
            "vsndef35 vsndef36 vsndef37 vsndef38 vsndef39 vsndef4 vsndef40 vsndef5 vsndef6 vsndef7 vsndef8 " +
            "vsndef9 vsourcebillcode vsourcerowno vsourcewastcode vsourcewastrowno vsrc2billcode vsrc2billrowno " +
            "vtransfercode vtranstypeu8rm vvehiclecode vvendbatchcode");

    private static final Set<String> IC_SALEOUT_L = fields(
            "cgeneralbid cgeneralhid cgenerallid clocationid corpoid corpvid csnqualitylevelid csnunitid dr " +
            "nassistnum ngrossnum nnum nshouldassistnum nshouldnum pk_group pk_serialcode ts vbarcode vbarcodesub " +
            "vboxbarcode vserialcode vsndef1 vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 vsndef15 vsndef16 " +
            "vsndef17 vsndef18 vsndef19 vsndef2 vsndef20 vsndef21 vsndef22 vsndef23 vsndef24 vsndef25 vsndef26 " +
            "vsndef27 vsndef28 vsndef29 vsndef3 vsndef30 vsndef31 vsndef32 vsndef33 vsndef34 vsndef35 vsndef36 " +
            "vsndef37 vsndef38 vsndef39 vsndef4 vsndef40 vsndef5 vsndef6 vsndef7 vsndef8 vsndef9");

    // NC65 BMF/dictionary fields; dr and ts are conventional columns and still need site verification.
    private static final Set<String> SCM_BATCHCODE = fields(
            "binqc bseal cmaterialoid cmaterialvid cqualitylevelid csourcebid csourcehid csourcetype " +
            "dinbounddate dproducedate dr dvalidate pk_batchcode pk_group tbatchtime tchecktime ts " +
            "vbatchcode vdef1 vdef10 vdef11 vdef12 vdef13 vdef14 vdef15 vdef16 vdef17 vdef18 vdef19 " +
            "vdef2 vdef20 vdef3 vdef4 vdef5 vdef6 vdef7 vdef8 vdef9 version vhashcode vnote " +
            "vprodbatchcode vsourcebillcode vsourcerowno vvendbatchcode");

    private static final Set<String> IC_TRANSIN_H = fields(
            "approver bdirecttranflag billmaker cbizid cbiztype cdptid cdptvid cgeneralhid corpoid corpvid " +
            "cothercalbodyoid cothercalbodyvid cotherwhid creationtime creator csendtypeid ctrantypeid " +
            "cwarehouseid cwhsmanagerid dbilldate dmakedate dr fbillflag fmodetype iprintcount modifiedtime " +
            "modifier ntotalnum ntotalpiece ntotalvolume ntotalweight pk_group pk_measware pk_org pk_org_v " +
            "taudittime ts vbillcode vdef1 vdef10 vdef11 vdef12 vdef13 vdef14 vdef15 vdef16 vdef17 vdef18 vdef19 " +
            "vdef2 vdef20 vdef3 vdef4 vdef5 vdef6 vdef7 vdef8 vdef9 vnote vtrantypecode");

    private static final Set<String> IC_TRANSIN_B = fields(
            "bassetcard bbarcodeclose bfixedasset bhasiabill bonroadflag bsourcelargess casscustid castunitid " +
            "cbodytranstypecode cbodywarehouseid ccorrespondbid ccorrespondcode ccorrespondhid ccorrespondrowno " +
            "ccorrespondtranstype ccorrespondtype ccurrencyid cffileid cfirstbillbid cfirstbillhid " +
            "cfirsttranstype cfirsttype cgeneralbid cgeneralhid cioliabilityoid cioliabilityvid cliabilityoid " +
            "cliabilityvid clocationid cmaterialoid cmaterialvid corigcurrencyid corpoid corpvid coutcalbodyoid " +
            "coutcalbodyvid cproductorid cprojectid cqualitylevelid crowno cselastunitid csettlecurrencyid " +
            "csnqualitylevelid csnunitid csourcebillbid csourcebillhid csourcetranstype csourcetype csrc2billbid " +
            "csrc2billhid csrc2billtype csrc2transtype csrcmaterialoid csrcmaterialvid cstateid cunitid cvendorid " +
            "cvmivenderid dbizdate dfirstbilldate dinbounddate dplanarrivedate dplanoutdate dproducedate dr " +
            "dvalidate flargess ibcversion idesatype nassistnum nbarcodenum ncorrespondastnum ncorrespondgrsnum " +
            "ncorrespondnum ncostmny ncostprice ncountnum ngrossnum nnetprice nnum norignetprice norigtaxnetprice " +
            "npiece nplannedmny nplannedprice nsettleprice nsettlepricenotax nshouldassistnum nshouldnum ntarenum " +
            "ntaxnetprice ntotalpicknum nvolume nweight pk_batchcode pk_group pk_measware pk_org pk_org_v " +
            "pk_packsort pk_serialcode tchecktime ts tsourcebodyts tsourceheadts vbatchcode vbatchcodenote " +
            "vbcdef1 vbcdef10 vbcdef11 vbcdef12 vbcdef13 vbcdef14 vbcdef15 vbcdef16 vbcdef17 vbcdef18 vbcdef19 " +
            "vbcdef2 vbcdef20 vbcdef3 vbcdef4 vbcdef5 vbcdef6 vbcdef7 vbcdef8 vbcdef9 vbdef1 vbdef10 vbdef11 " +
            "vbdef12 vbdef13 vbdef14 vbdef15 vbdef16 vbdef17 vbdef18 vbdef19 vbdef2 vbdef20 vbdef3 vbdef4 vbdef5 " +
            "vbdef6 vbdef7 vbdef8 vbdef9 vbillbarcode vbilltypeu8rm vchangerate vfirstbillcode vfirstrowno vfree1 " +
            "vfree10 vfree2 vfree3 vfree4 vfree5 vfree6 vfree7 vfree8 vfree9 vnotebody vserialcode vsndef1 " +
            "vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 vsndef15 vsndef16 vsndef17 vsndef18 vsndef19 vsndef2 " +
            "vsndef20 vsndef21 vsndef22 vsndef23 vsndef24 vsndef25 vsndef26 vsndef27 vsndef28 vsndef29 vsndef3 " +
            "vsndef30 vsndef31 vsndef32 vsndef33 vsndef34 vsndef35 vsndef36 vsndef37 vsndef38 vsndef39 vsndef4 " +
            "vsndef40 vsndef5 vsndef6 vsndef7 vsndef8 vsndef9 vsourcebillcode vsourcerowno vsrc2billcode " +
            "vsrc2billrowno vtransfercode vvehiclecode vvendbatchcode");

    private static final Set<String> IC_TRANSIN_L = fields(
            "cgeneralbid cgeneralhid cgenerallid clocationid corpoid corpvid csnqualitylevelid csnunitid dr " +
            "nassistnum ngrossnum nnum nshouldassistnum nshouldnum pk_group pk_serialcode ts vbarcode vbarcodesub " +
            "vboxbarcode vserialcode vsndef1 vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 vsndef15 vsndef16 " +
            "vsndef17 vsndef18 vsndef19 vsndef2 vsndef20 vsndef21 vsndef22 vsndef23 vsndef24 vsndef25 vsndef26 " +
            "vsndef27 vsndef28 vsndef29 vsndef3 vsndef30 vsndef31 vsndef32 vsndef33 vsndef34 vsndef35 vsndef36 " +
            "vsndef37 vsndef38 vsndef39 vsndef4 vsndef40 vsndef5 vsndef6 vsndef7 vsndef8 vsndef9");

    private static final Set<String> IC_OPENBAL_H = fields(
            "approver billmaker cbizid ccostdomainid cdptid cdptvid cgeneralhid corpoid corpvid creationtime " +
            "creator ctrantypeid cvendorid cwarehouseid cwhsmanagerid dbilldate dmakedate dr fbillflag " +
            "iprintcount modifiedtime modifier ntotalnum pk_group pk_measware pk_org pk_org_v taudittime ts " +
            "vbillcode vdef1 vdef10 vdef11 vdef12 vdef13 vdef14 vdef15 vdef16 vdef17 vdef18 vdef19 vdef2 vdef20 " +
            "vdef3 vdef4 vdef5 vdef6 vdef7 vdef8 vdef9 vnote vtrantypecode");

    private static final Set<String> IC_OPENBAL_B = fields(
            "bbarcodeclose bcseal casscustid castunitid cbodytranstypecode cbodywarehouseid cffileid cgeneralbid " +
            "cgeneralhid cliabilityoid cliabilityvid clocationid cmaterialoid cmaterialvid corpoid corpvid " +
            "cproductorid cprojectid cqualitylevelid crowno csnqualitylevelid csnunitid cstateid ctplcustomerid " +
            "cunitid cvendorid cvmivenderid dbizdate dinbounddate dproducedate dr dvalidate fbillrowflag " +
            "ibcversion naccumvminum nassistnum nbarcodenum ncorrespondastnum ncorrespondgrsnum ncorrespondnum " +
            "ncostmny ncostprice ncountnum ngrossnum nnum npiece nshouldassistnum nshouldnum ntarenum nvolume " +
            "nweight pk_batchcode pk_group pk_measware pk_org pk_org_v pk_packsort pk_serialcode tbcts tchecktime " +
            "ts vbatchcode vbatchcodenote vbcdef1 vbcdef10 vbcdef11 vbcdef12 vbcdef13 vbcdef14 vbcdef15 vbcdef16 " +
            "vbcdef17 vbcdef18 vbcdef19 vbcdef2 vbcdef20 vbcdef3 vbcdef4 vbcdef5 vbcdef6 vbcdef7 vbcdef8 vbcdef9 " +
            "vbdef1 vbdef10 vbdef11 vbdef12 vbdef13 vbdef14 vbdef15 vbdef16 vbdef17 vbdef18 vbdef19 vbdef2 " +
            "vbdef20 vbdef3 vbdef4 vbdef5 vbdef6 vbdef7 vbdef8 vbdef9 vbillbarcode vchangerate vfree1 vfree10 " +
            "vfree2 vfree3 vfree4 vfree5 vfree6 vfree7 vfree8 vfree9 vnotebody vserialcode vsndef1 vsndef10 " +
            "vsndef11 vsndef12 vsndef13 vsndef14 vsndef15 vsndef16 vsndef17 vsndef18 vsndef19 vsndef2 vsndef20 " +
            "vsndef21 vsndef22 vsndef23 vsndef24 vsndef25 vsndef26 vsndef27 vsndef28 vsndef29 vsndef3 vsndef30 " +
            "vsndef31 vsndef32 vsndef33 vsndef34 vsndef35 vsndef36 vsndef37 vsndef38 vsndef39 vsndef4 vsndef40 " +
            "vsndef5 vsndef6 vsndef7 vsndef8 vsndef9 vvendbatchcode");

    private static final Set<String> IC_OPENBAL_L = fields(
            "cgeneralbid cgeneralhid cgenerallid clocationid corpoid corpvid csnqualitylevelid csnunitid dr " +
            "nassistnum ngrossnum nnum nshouldassistnum nshouldnum pk_group pk_serialcode ts vbarcode vbarcodesub " +
            "vboxbarcode vserialcode vsndef1 vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 vsndef15 vsndef16 " +
            "vsndef17 vsndef18 vsndef19 vsndef2 vsndef20 vsndef21 vsndef22 vsndef23 vsndef24 vsndef25 vsndef26 " +
            "vsndef27 vsndef28 vsndef29 vsndef3 vsndef30 vsndef31 vsndef32 vsndef33 vsndef34 vsndef35 vsndef36 " +
            "vsndef37 vsndef38 vsndef39 vsndef4 vsndef40 vsndef5 vsndef6 vsndef7 vsndef8 vsndef9");

    // Local NC m4q BMF fields; relationship and lifecycle columns require site schema verification.
    private static final Set<String> IC_LOCADJUST_H = fields(
            "billmaker cgeneralhid corpoid corpvid ctrantypeid cwarehouseid cwhsmanagerid dbilldate " +
            "dmakedate dr iprintcount modifiedtime modifier pk_group pk_org pk_org_v ts vbillcode " +
            "vdef1 vdef10 vdef11 vdef12 vdef13 vdef14 vdef15 vdef16 vdef17 vdef18 vdef19 vdef2 " +
            "vdef20 vdef3 vdef4 vdef5 vdef6 vdef7 vdef8 vdef9 vnote vtrantypecode");

    private static final Set<String> IC_LOCADJUST_B = fields(
            "casscustid castunitid cffileid cgeneralbid cgeneralhid clocationid clocationinid cmaterialoid " +
            "cmaterialvid corpoid corpvid cproductorid cprojectid cprojectstatusid cqualitylevelid crowno " +
            "csnqualitylevelid csnunitid cstateid ctplcustomerid cunitid cvendorid cvmivenderid dbizdate " +
            "dproducedate dr dvalidate nassistnum ngrossnum nnum nshouldassistnum nshouldnum pk_batchcode " +
            "pk_group pk_org pk_org_v pk_serialcode ts vbatchcode vbdef1 vbdef10 vbdef11 vbdef12 vbdef13 " +
            "vbdef14 vbdef15 vbdef16 vbdef17 vbdef18 vbdef19 vbdef2 vbdef20 vbdef3 vbdef4 vbdef5 vbdef6 " +
            "vbdef7 vbdef8 vbdef9 vchangerate vfree1 vfree10 vfree2 vfree3 vfree4 vfree5 vfree6 vfree7 " +
            "vfree8 vfree9 vnotebody vserialcode vsndef1 vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 " +
            "vsndef15 vsndef16 vsndef17 vsndef18 vsndef19 vsndef2 vsndef20 vsndef21 vsndef22 vsndef23 " +
            "vsndef24 vsndef25 vsndef26 vsndef27 vsndef28 vsndef29 vsndef3 vsndef30 vsndef31 vsndef32 " +
            "vsndef33 vsndef34 vsndef35 vsndef36 vsndef37 vsndef38 vsndef39 vsndef4 vsndef40 vsndef5 " +
            "vsndef6 vsndef7 vsndef8 vsndef9 vvendbatchcode");

    private static final Set<String> IC_LOCADJUST_L = fields(
            "cgeneralbid cgeneralhid cgenerallid clocationid corpoid corpvid csnqualitylevelid csnunitid dr " +
            "nassistnum ngrossnum nnum pk_group pk_serialcode ts vbarcode vbarcodesub vboxbarcode vserialcode " +
            "vsndef1 vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 vsndef15 vsndef16 vsndef17 vsndef18 " +
            "vsndef19 vsndef2 vsndef20 vsndef21 vsndef22 vsndef23 vsndef24 vsndef25 vsndef26 vsndef27 " +
            "vsndef28 vsndef29 vsndef3 vsndef30 vsndef31 vsndef32 vsndef33 vsndef34 vsndef35 vsndef36 " +
            "vsndef37 vsndef38 vsndef39 vsndef4 vsndef40 vsndef5 vsndef6 vsndef7 vsndef8 vsndef9");

    // Local NC m4a BMF fields; relationship and lifecycle columns require site schema verification.
    private static final Set<String> IC_GENERALIN_H = fields(
            "approver billmaker cbizid cdptid cdptvid cgeneralhid corpoid corpvid cothercalbodyoid " +
            "cothercalbodyvid cotherwhid creationtime creator ctrantypeid cwarehouseid cwhsmanagerid dbilldate " +
            "dmakedate dr fbillflag iprintcount modifiedtime modifier ntotalnum ntotalpiece ntotalvolume " +
            "ntotalweight pk_group pk_measware pk_org pk_org_v taudittime ts vbillcode vdef1 vdef10 vdef11 " +
            "vdef12 vdef13 vdef14 vdef15 vdef16 vdef17 vdef18 vdef19 vdef2 vdef20 vdef3 vdef4 vdef5 vdef6 " +
            "vdef7 vdef8 vdef9 vnote vtrantypecode");

    private static final Set<String> IC_GENERALIN_B = fields(
            "bbarcodeclose bcseal bhasiabill bonroadflag casscustid castunitid cbodytranstypecode cbodywarehouseid " +
            "ccorrespondbid ccorrespondcode ccorrespondhid ccorrespondrowno ccorrespondtranstype ccorrespondtype " +
            "cffileid cfirstbillbid cfirstbillhid cfirsttranstype cfirsttype cgeneralbid cgeneralhid cliabilityoid " +
            "cliabilityvid clocationid cmaterialoid cmaterialvid corpoid corpvid cproductorid cprojectid " +
            "cqualitylevelid crowno cselastunitid csnqualitylevelid csnunitid csourcebillbid csourcebillhid " +
            "csourcetranstype csourcetype csrc2billbid csrc2billhid csrc2billtype csrc2transtype cstateid " +
            "ctplcustomerid cunitid cvendorid cvmivenderid dbizdate dinbounddate dproducedate dr dvalidate " +
            "ibcversion idesatype nassistnum nbarcodenum ncorrespondastnum ncorrespondgrsnum ncorrespondnum " +
            "ncostmny ncostprice ncountnum nfeesettlecount ngrossnum nnum npiece nplannedmny nplannedprice " +
            "nshouldassistnum nshouldnum ntarenum ntotalpicknum nvolume nweight pk_batchcode pk_group " +
            "pk_measware pk_org pk_org_v pk_packsort pk_serialcode tbcts tchecktime ts tsourcebodyts tsourceheadts " +
            "vbatchcode vbatchcodenote vbcdef1 vbcdef10 vbcdef11 vbcdef12 vbcdef13 vbcdef14 vbcdef15 vbcdef16 " +
            "vbcdef17 vbcdef18 vbcdef19 vbcdef2 vbcdef20 vbcdef3 vbcdef4 vbcdef5 vbcdef6 vbcdef7 vbcdef8 " +
            "vbcdef9 vbdef1 vbdef10 vbdef11 vbdef12 vbdef13 vbdef14 vbdef15 vbdef16 vbdef17 vbdef18 vbdef19 " +
            "vbdef2 vbdef20 vbdef3 vbdef4 vbdef5 vbdef6 vbdef7 vbdef8 vbdef9 vbillbarcode vbilltypeu8rm " +
            "vchangerate vfirstbillcode vfirstrowno vfree1 vfree10 vfree2 vfree3 vfree4 vfree5 vfree6 vfree7 " +
            "vfree8 vfree9 vnotebody vserialcode vsndef1 vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 " +
            "vsndef15 vsndef16 vsndef17 vsndef18 vsndef19 vsndef2 vsndef20 vsndef21 vsndef22 vsndef23 " +
            "vsndef24 vsndef25 vsndef26 vsndef27 vsndef28 vsndef29 vsndef3 vsndef30 vsndef31 vsndef32 " +
            "vsndef33 vsndef34 vsndef35 vsndef36 vsndef37 vsndef38 vsndef39 vsndef4 vsndef40 vsndef5 " +
            "vsndef6 vsndef7 vsndef8 vsndef9 vsourcebillcode vsourcerowno vsrc2billcode vsrc2billrowno " +
            "vtransfercode vtranstypeu8rm vvehiclecode vvendbatchcode");

    private static final Set<String> IC_GENERALIN_L = fields(
            "cgeneralbid cgeneralhid cgenerallid clocationid corpoid corpvid csnqualitylevelid csnunitid dr " +
            "nassistnum ngrossnum nnum nshouldassistnum nshouldnum pk_group pk_serialcode ts vbarcode vbarcodesub " +
            "vboxbarcode vserialcode vsndef1 vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 vsndef15 vsndef16 " +
            "vsndef17 vsndef18 vsndef19 vsndef2 vsndef20 vsndef21 vsndef22 vsndef23 vsndef24 vsndef25 vsndef26 " +
            "vsndef27 vsndef28 vsndef29 vsndef3 vsndef30 vsndef31 vsndef32 vsndef33 vsndef34 vsndef35 vsndef36 " +
            "vsndef37 vsndef38 vsndef39 vsndef4 vsndef40 vsndef5 vsndef6 vsndef7 vsndef8 vsndef9");

    // Local NC m46 BMF fields; relationship and lifecycle columns require site schema verification.
    private static final Set<String> IC_FINPRODIN_H = fields(
            "approver billmaker cbizid cdptid cdptvid cgeneralhid corpoid corpvid cprocalbodyoid cprocalbodyvid " +
            "cprowarehouseid creationtime creator ctrantypeid cwarehouseid cwhsmanagerid dbilldate dmakedate dr " +
            "fbillflag iprintcount modifiedtime modifier ntotalnum ntotalpiece ntotalvolume ntotalweight " +
            "orgcostdomainid orgfinanceid pk_group pk_measware pk_org pk_org_v procostdomainid profinanceid " +
            "taudittime ts vbillcode vdef1 vdef10 vdef11 vdef12 vdef13 vdef14 vdef15 vdef16 vdef17 vdef18 vdef19 " +
            "vdef2 vdef20 vdef3 vdef4 vdef5 vdef6 vdef7 vdef8 vdef9 vnote vtrantypecode");

    private static final Set<String> IC_FINPRODIN_B = fields(
            "bbarcodeclose bcseal bonroadflag breworkflag casscustid castunitid cbodytranstypecode cbodywarehouseid " +
            "ccorrespondbid ccorrespondcode ccorrespondhid ccorrespondrowno ccorrespondtranstype ccorrespondtype " +
            "cffileid cfirstbillbid cfirstbillhid cfirsttranstype cfirsttype cgeneralbid cgeneralhid chandoverbid " +
            "chandoverhid chandvoertranstype cioliabilityoid cioliabilityvid cliabilityoid cliabilityvid clocationid " +
            "cmaterialoid cmaterialvid cmffileid corpoid corpvid cprodasscustid cprodproductorid cprodprojectid " +
            "cproductid cproductorid cprodvendorid cprojectid cqualitylevelid crowno cselastunitid csnqualitylevelid " +
            "csnunitid csourcebillbid csourcebillhid csourcematerialoid csourcetranstype csourcetype csrc2billbid " +
            "csrc2billhid csrc2billtype csrc2transtype csrcmaterialoid csrcmaterialvid cstateid ctplcustomerid " +
            "cunitid cvendorid cvmivenderid cworkcenterid dbizdate dinbounddate dproducedate dr dvalidate flargess " +
            "fproductclass ibcversion idesatype nassistnum nbarcodenum ncorrespondastnum ncorrespondgrsnum " +
            "ncorrespondnum ncostmny ncostprice ncountnum ngrossnum nnum npickupnum npiece nplannedmny nplannedprice " +
            "nshouldassistnum nshouldnum ntarenum nvolume nweight pk_batchcode pk_group pk_measware pk_org pk_org_v " +
            "pk_packsort pk_serialcode tbcts tchecktime ts tsourcebodyts tsourceheadts vbatchcode vbatchcodenote " +
            "vbcdef1 vbcdef10 vbcdef11 vbcdef12 vbcdef13 vbcdef14 vbcdef15 vbcdef16 vbcdef17 vbcdef18 vbcdef19 " +
            "vbcdef2 vbcdef20 vbcdef3 vbcdef4 vbcdef5 vbcdef6 vbcdef7 vbcdef8 vbcdef9 vbdef1 vbdef10 vbdef11 vbdef12 " +
            "vbdef13 vbdef14 vbdef15 vbdef16 vbdef17 vbdef18 vbdef19 vbdef2 vbdef20 vbdef3 vbdef4 vbdef5 vbdef6 " +
            "vbdef7 vbdef8 vbdef9 vbillbarcode vbilltypeu8rm vchangerate vexigencybid vexigencycode vexigencyhid " +
            "vexigencyrowno vexigencytype vfirstbillcode vfirstrowno vfree1 vfree10 vfree2 vfree3 vfree4 vfree5 " +
            "vfree6 vfree7 vfree8 vfree9 vhandovercode vnotebody vprodfree1 vprodfree10 vprodfree2 vprodfree3 " +
            "vprodfree4 vprodfree5 vprodfree6 vprodfree7 vprodfree8 vprodfree9 vproductbatch vserialcode vsndef1 " +
            "vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 vsndef15 vsndef16 vsndef17 vsndef18 vsndef19 vsndef2 " +
            "vsndef20 vsndef21 vsndef22 vsndef23 vsndef24 vsndef25 vsndef26 vsndef27 vsndef28 vsndef29 vsndef3 " +
            "vsndef30 vsndef31 vsndef32 vsndef33 vsndef34 vsndef35 vsndef36 vsndef37 vsndef38 vsndef39 vsndef4 " +
            "vsndef40 vsndef5 vsndef6 vsndef7 vsndef8 vsndef9 vsourcebillcode vsourcerowno vsrc2billcode " +
            "vsrc2billrowno vtransfercode vtranstypeu8rm vvehiclecode vvendbatchcode");

    private static final Set<String> IC_FINPRODIN_L = fields(
            "cgeneralbid cgeneralhid cgenerallid clocationid corpoid corpvid csnqualitylevelid csnunitid dr " +
            "nassistnum ngrossnum nnum nshouldassistnum nshouldnum pk_serialcode ts vbarcode vbarcodesub vboxbarcode " +
            "vserialcode vsndef1 vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 vsndef15 vsndef16 vsndef17 vsndef18 " +
            "vsndef19 vsndef2 vsndef20 vsndef21 vsndef22 vsndef23 vsndef24 vsndef25 vsndef26 vsndef27 vsndef28 " +
            "vsndef29 vsndef3 vsndef30 vsndef31 vsndef32 vsndef33 vsndef34 vsndef35 vsndef36 vsndef37 vsndef38 " +
            "vsndef39 vsndef4 vsndef40 vsndef5 vsndef6 vsndef7 vsndef8 vsndef9");

    // Local NC m47 BMF fields; relationship and lifecycle columns require site schema verification.
    private static final Set<String> IC_SUBCONTIN_H = fields(
            "approver billmaker bverify cbizid cbiztype ccostdomainid cdptid cdptvid cgeneralhid corpoid corpvid " +
            "creationtime creator cscwarehouseid ctrantypeid cvendorid cwarehouseid cwhsmanagerid dbilldate dmakedate " +
            "dr fbillflag freplenishflag modifiedtime modifier ntotalnum ntotalpiece ntotalvolume ntotalweight " +
            "pk_group pk_measware pk_org pk_org_v taudittime ts vbillcode vdef1 vdef10 vdef11 vdef12 vdef13 vdef14 " +
            "vdef15 vdef16 vdef17 vdef18 vdef19 vdef2 vdef20 vdef3 vdef4 vdef5 vdef6 vdef7 vdef8 vdef9 vnote " +
            "vtrantypecode");

    private static final Set<String> IC_SUBCONTIN_B = fields(
            "bcseal bonroadflag breworkflag carriveorder_bbid casscustid castunitid cbodytranstypecode " +
            "cbodywarehouseid ccorrespondbid ccorrespondcode ccorrespondhid ccorrespondrowno ccorrespondtranstype " +
            "ccorrespondtype cffileid cfirstbillbid cfirstbillhid cfirsttranstype cfirsttype cgeneralbid cgeneralhid " +
            "clocationid cmainasscustid cmainmaterialoid cmainproductorid cmainprojectid cmainvendorid cmaterialoid " +
            "cmaterialvid cmffileid corpoid corpvid cproductorid cprojectid cqualitylevelid crowno csnqualitylevelid " +
            "csnunitid csourcebillbid csourcebillhid csourcetranstype csourcetype csrc2billbid csrc2billhid " +
            "csrc2billtype csrc2transtype cstateid cunitid cvendorid cworkcenterid dbizdate dinbounddate dproducedate " +
            "dr dvalidate foutputtype ibcversion nassistnum nbarcodenum ncorrespondastnum ncorrespondgrsnum " +
            "ncorrespondnum ncostmny ncostprice ncountnum ngrossnum ninvoicenum nnum npiece nplannedmny nplannedprice " +
            "nshouldassistnum nshouldnum nsignnum ntarenum ntotalpicknum nvolume nweight pk_batchcode pk_group " +
            "pk_measware pk_org pk_org_v pk_serialcode tbcts tchecktime ts tsourcebodyts tsourceheadts vbatchcode " +
            "vbatchcodenote vbcdef1 vbcdef10 vbcdef11 vbcdef12 vbcdef13 vbcdef14 vbcdef15 vbcdef16 vbcdef17 vbcdef18 " +
            "vbcdef19 vbcdef2 vbcdef20 vbcdef3 vbcdef4 vbcdef5 vbcdef6 vbcdef7 vbcdef8 vbcdef9 vbdef1 vbdef10 vbdef11 " +
            "vbdef12 vbdef13 vbdef14 vbdef15 vbdef16 vbdef17 vbdef18 vbdef19 vbdef2 vbdef20 vbdef3 vbdef4 vbdef5 " +
            "vbdef6 vbdef7 vbdef8 vbdef9 vbillbarcode vchangerate vexigencybid vexigencycode vexigencyhid " +
            "vexigencyrowno vexigencytype vfirstbillcode vfirstrowno vfree1 vfree10 vfree2 vfree3 vfree4 vfree5 " +
            "vfree6 vfree7 vfree8 vfree9 vmainfree1 vmainfree10 vmainfree2 vmainfree3 vmainfree4 vmainfree5 " +
            "vmainfree6 vmainfree7 vmainfree8 vmainfree9 vnotebody vproductbatch vproductbill vserialcode vsndef1 " +
            "vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 vsndef15 vsndef16 vsndef17 vsndef18 vsndef19 vsndef2 " +
            "vsndef20 vsndef21 vsndef22 vsndef23 vsndef24 vsndef25 vsndef26 vsndef27 vsndef28 vsndef29 vsndef3 " +
            "vsndef30 vsndef31 vsndef32 vsndef33 vsndef34 vsndef35 vsndef36 vsndef37 vsndef38 vsndef39 vsndef4 " +
            "vsndef40 vsndef5 vsndef6 vsndef7 vsndef8 vsndef9 vsourcebillcode vsourcerowno vsrc2billcode " +
            "vsrc2billrowno vvendbatchcode");

    private static final Set<String> IC_SUBCONTIN_L = fields(
            "cgeneralbid cgeneralhid cgenerallid clocationid corpoid corpvid csnqualitylevelid csnunitid dr " +
            "nassistnum ngrossnum nnum nshouldassistnum nshouldnum pk_group pk_serialcode ts vbarcode vbarcodesub " +
            "vboxbarcode vserialcode vsndef1 vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 vsndef15 vsndef16 vsndef17 " +
            "vsndef18 vsndef19 vsndef2 vsndef20 vsndef21 vsndef22 vsndef23 vsndef24 vsndef25 vsndef26 vsndef27 " +
            "vsndef28 vsndef29 vsndef3 vsndef30 vsndef31 vsndef32 vsndef33 vsndef34 vsndef35 vsndef36 vsndef37 " +
            "vsndef38 vsndef39 vsndef4 vsndef40 vsndef5 vsndef6 vsndef7 vsndef8 vsndef9");

    // Local NC m4i BMF fields; relationship and lifecycle columns require site schema verification.
    private static final Set<String> IC_GENERALOUT_H = fields(
            "approver billmaker cbizid cdilivertypeid cdptid cdptvid cgeneralhid corpoid corpvid cothercalbodyoid " +
            "cothercalbodyvid cotherwhid creationtime creator ctrantypeid cwarehouseid cwhsmanagerid dbilldate " +
            "dmakedate dr fbillflag iprintcount modifiedtime modifier ntotalnum ntotalpiece ntotalvolume ntotalweight " +
            "pk_group pk_measware pk_org pk_org_v taudittime trafficorgoid trafficorgvid ts vbillcode vdef1 vdef10 " +
            "vdef11 vdef12 vdef13 vdef14 vdef15 vdef16 vdef17 vdef18 vdef19 vdef2 vdef20 vdef3 vdef4 vdef5 vdef6 " +
            "vdef7 vdef8 vdef9 vdiliveraddress vnote vtrantypecode");

    private static final Set<String> IC_GENERALOUT_B = fields(
            "bbarcodeclose bonroadflag casscustid castunitid cbodytranstypecode cbodywarehouseid ccorrespondbid " +
            "ccorrespondcode ccorrespondhid ccorrespondrowno ccorrespondtranstype ccorrespondtype ccostobject " +
            "cffileid cfirstbillbid cfirstbillhid cfirsttranstype cfirsttype cgeneralbid cgeneralhid cliabilityoid " +
            "cliabilityvid clocationid cmaterialoid cmaterialvid corpoid corpvid costclsidreason cproductorid " +
            "cprojectid cprojecttaskid cqualitylevelid creceieveid creceiveareaid creceivepointid crowno " +
            "cselastunitid csnqualitylevelid csnunitid csourcebillbid csourcebillhid csourcetranstype csourcetype " +
            "csrc2billbid csrc2billhid csrc2billtype csrc2transtype cstateid csumid ctplcustomerid cunitid cvendorid " +
            "cvmivenderid dbizdate dinbounddate dproducedate dr dvalidate ibcversion idesatype naccumvminum " +
            "nassistnum nbarcodenum ncorrespondastnum ncorrespondgrsnum ncorrespondnum ncostmny ncostprice ncountnum " +
            "ngrossnum nnum npiece nplannedmny nplannedprice nshouldassistnum nshouldnum ntarenum ntotaltrannum " +
            "nvolume nweight pk_batchcode pk_group pk_measware pk_org pk_org_v pk_packsort pk_serialcode tchecktime " +
            "ts tsourcebodyts tsourceheadts vbatchcode vbatchcodenote vbcdef1 vbcdef10 vbcdef11 vbcdef12 vbcdef13 " +
            "vbcdef14 vbcdef15 vbcdef16 vbcdef17 vbcdef18 vbcdef19 vbcdef2 vbcdef20 vbcdef3 vbcdef4 vbcdef5 vbcdef6 " +
            "vbcdef7 vbcdef8 vbcdef9 vbdef1 vbdef10 vbdef11 vbdef12 vbdef13 vbdef14 vbdef15 vbdef16 vbdef17 vbdef18 " +
            "vbdef19 vbdef2 vbdef20 vbdef3 vbdef4 vbdef5 vbdef6 vbdef7 vbdef8 vbdef9 vbillbarcode vbilltypeu8rm " +
            "vchangerate vexigencybid vexigencycode vexigencyhid vexigencyrowno vexigencytype vfirstbillcode " +
            "vfirstrowno vfree1 vfree10 vfree2 vfree3 vfree4 vfree5 vfree6 vfree7 vfree8 vfree9 vnotebody " +
            "vproductbatch vreceiveaddress vserialcode vsndef1 vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 vsndef15 " +
            "vsndef16 vsndef17 vsndef18 vsndef19 vsndef2 vsndef20 vsndef21 vsndef22 vsndef23 vsndef24 vsndef25 " +
            "vsndef26 vsndef27 vsndef28 vsndef29 vsndef3 vsndef30 vsndef31 vsndef32 vsndef33 vsndef34 vsndef35 " +
            "vsndef36 vsndef37 vsndef38 vsndef39 vsndef4 vsndef40 vsndef5 vsndef6 vsndef7 vsndef8 vsndef9 " +
            "vsourcebillcode vsourcerowno vsrc2billcode vsrc2billrowno vtransfercode vtranstypeu8rm vvehiclecode " +
            "vvendbatchcode");

    private static final Set<String> IC_GENERALOUT_L = fields(
            "cgeneralbid cgeneralhid cgenerallid clocationid corpoid corpvid csnqualitylevelid csnunitid dr " +
            "nassistnum ngrossnum nnum nshouldassistnum nshouldnum pk_group pk_serialcode ts vbarcode vbarcodesub " +
            "vboxbarcode vserialcode vsndef1 vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 vsndef15 vsndef16 vsndef17 " +
            "vsndef18 vsndef19 vsndef2 vsndef20 vsndef21 vsndef22 vsndef23 vsndef24 vsndef25 vsndef26 vsndef27 " +
            "vsndef28 vsndef29 vsndef3 vsndef30 vsndef31 vsndef32 vsndef33 vsndef34 vsndef35 vsndef36 vsndef37 " +
            "vsndef38 vsndef39 vsndef4 vsndef40 vsndef5 vsndef6 vsndef7 vsndef8 vsndef9");

    // Local NC m4d BMF fields; relationship and lifecycle columns require site schema verification.
    private static final Set<String> IC_MATERIAL_H = fields(
            "approver billmaker cavtype cbizid cconstructvendorid ccostdomainoid cdptid cdptvid cdrawcalbodyoid " +
            "cdrawcalbodyvid cdrawwarehouseid cfanaceorgoid cfanaceorgvid cgeneralhid corpoid corpvid creationtime " +
            "creator crefgeneralhid ctrantypeid cwarehouseid cwhsmanagerid dbilldate dmakedate dr fbillflag " +
            "iprintcount modifiedtime modifier ntotalnum ntotalpiece ntotalvolume ntotalweight orgcostdomainid " +
            "orgfinanceid pk_group pk_measware pk_org pk_org_v taudittime trefts ts vbillcode vdef1 vdef10 vdef11 " +
            "vdef12 vdef13 vdef14 vdef15 vdef16 vdef17 vdef18 vdef19 vdef2 vdef20 vdef3 vdef4 vdef5 vdef6 vdef7 vdef8 " +
            "vdef9 vnote vtrantypecode");

    private static final Set<String> IC_MATERIAL_B = fields(
            "bassetcard bbarcodeclose bcanreplace bcseal bmaterialtype bomversion bonroadflag breworkflag casscustid " +
            "castunitid cbodytranstypecode cbodywarehouseid cbommaterialvid ccorrespondbid ccorrespondcode " +
            "ccorrespondhid ccorrespondrowno ccorrespondtranstype ccorrespondtype ccostobject ccurrencyid cffileid " +
            "cfirstbillbid cfirstbillhid cfirsttranstype cfirsttype cgeneralbid cgeneralhid chandoverbid chandoverhid " +
            "chandvoertranstype cioliabilityoid cioliabilityvid cliabilityoid cliabilityvid clocationid cmaterialoid " +
            "cmaterialvid cmffileid corpoid corpvid cpickmbid cpickmcode cpickmhid cpickmrowno cpickmtranstype " +
            "cprodasscustid cprodproductorid cprodprojectid cproductorid cprodvendorid cprojectid cprojecttaskid " +
            "cqualitylevelid crcid creportbid creporthid crowno cselastunitid csnqualitylevelid csnunitid " +
            "csourcebillbid csourcebillhid csourcetranstype csourcetype csrc2billbid csrc2billhid csrc2billtype " +
            "csrc2transtype csrcmaterialoid csrcmaterialvid cstateid csumid ctplcustomerid cunitid cvendorid " +
            "cvmivenderid cworkcenterid cworkorderbid cworkordercode cworkorderhid cworkorderrowno cworkordertrantype " +
            "cworksiteid cwp dbizdate dinbounddate dproducedate dr drequiredate dvalidate ibcversion idesatype " +
            "naccumvminum nassistnum nbarcodenum nbatchjoinnum ncorrespondastnum ncorrespondgrsnum ncorrespondnum " +
            "ncostmny ncostprice ncountnum ngrossnum nnum npiece nplannedmny nplannedprice nshouldassistnum " +
            "nshouldnum ntarenum nvolume nweight pk_batchcode pk_cbsnode pk_group pk_measware pk_org pk_org_v " +
            "pk_packsort pk_serialcode productpk tbcts tchecktime theadts ts tsourcebodyts tsourceheadts vbatchcode " +
            "vbatchcodenote vbcdef1 vbcdef10 vbcdef11 vbcdef12 vbcdef13 vbcdef14 vbcdef15 vbcdef16 vbcdef17 vbcdef18 " +
            "vbcdef19 vbcdef2 vbcdef20 vbcdef3 vbcdef4 vbcdef5 vbcdef6 vbcdef7 vbcdef8 vbcdef9 vbdef1 vbdef10 vbdef11 " +
            "vbdef12 vbdef13 vbdef14 vbdef15 vbdef16 vbdef17 vbdef18 vbdef19 vbdef2 vbdef20 vbdef3 vbdef4 vbdef5 " +
            "vbdef6 vbdef7 vbdef8 vbdef9 vbillbarcode vbilltypeu8rm vchangerate vcontractcode vexigencybid " +
            "vexigencycode vexigencyhid vexigencyrowno vexigencytype vfirstbillcode vfirstrowno vfree1 vfree10 vfree2 " +
            "vfree3 vfree4 vfree5 vfree6 vfree7 vfree8 vfree9 vhandovercode vnotebody vprodfree1 vprodfree10 " +
            "vprodfree2 vprodfree3 vprodfree4 vprodfree5 vprodfree6 vprodfree7 vprodfree8 vprodfree9 vproductbatch " +
            "vserialcode vsndef1 vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 vsndef15 vsndef16 vsndef17 vsndef18 " +
            "vsndef19 vsndef2 vsndef20 vsndef21 vsndef22 vsndef23 vsndef24 vsndef25 vsndef26 vsndef27 vsndef28 " +
            "vsndef29 vsndef3 vsndef30 vsndef31 vsndef32 vsndef33 vsndef34 vsndef35 vsndef36 vsndef37 vsndef38 " +
            "vsndef39 vsndef4 vsndef40 vsndef5 vsndef6 vsndef7 vsndef8 vsndef9 vsourcebillcode vsourcerowno " +
            "vsrc2billcode vsrc2billrowno vtransfercode vtranstypeu8rm vvehiclecode vvendbatchcode");

    private static final Set<String> IC_MATERIAL_L = fields(
            "cgeneralbid cgeneralhid cgenerallid clocationid corpoid corpvid csnqualitylevelid csnunitid dr " +
            "nassistnum ngrossnum nnum nshouldassistnum nshouldnum pk_group pk_serialcode ts vbarcode vbarcodesub " +
            "vboxbarcode vserialcode vsndef1 vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 vsndef15 vsndef16 vsndef17 " +
            "vsndef18 vsndef19 vsndef2 vsndef20 vsndef21 vsndef22 vsndef23 vsndef24 vsndef25 vsndef26 vsndef27 " +
            "vsndef28 vsndef29 vsndef3 vsndef30 vsndef31 vsndef32 vsndef33 vsndef34 vsndef35 vsndef36 vsndef37 " +
            "vsndef38 vsndef39 vsndef4 vsndef40 vsndef5 vsndef6 vsndef7 vsndef8 vsndef9");

    // Local NC m4y BMF fields; relationship and lifecycle columns require site schema verification.
    private static final Set<String> IC_TRANSOUT_H = fields(
            "approver bdirecttranflag billmaker boutretflag cbizid cbiztype cdilivertypeid cdptid cdptvid cgeneralhid " +
            "corpoid corpvid cothercalbodyoid cothercalbodyvid cotherwhid creationtime creator ctrancustid " +
            "ctrantypeid cwarehouseid cwhsmanagerid dbilldate dmakedate dr fbillflag fmodetype iprintcount " +
            "modifiedtime modifier ntotalnum ntotalpiece ntotalvolume ntotalweight pk_group pk_measware pk_org " +
            "pk_org_v taudittime trafficorgoid trafficorgvid ts vbillcode vdef1 vdef10 vdef11 vdef12 vdef13 vdef14 " +
            "vdef15 vdef16 vdef17 vdef18 vdef19 vdef2 vdef20 vdef3 vdef4 vdef5 vdef6 vdef7 vdef8 vdef9 " +
            "vdiliveraddress vnote vtrantypecode");

    private static final Set<String> IC_TRANSOUT_B = fields(
            "bbarcodeclose bonroadflag bsourcelargess btranendflag casscustid castunitid cbodytranstypecode " +
            "cbodywarehouseid ccorrespondbid ccorrespondcode ccorrespondhid ccorrespondrowno ccorrespondtranstype " +
            "ccorrespondtype cffileid cfirstbillbid cfirstbillhid cfirsttranstype cfirsttype cgeneralbid cgeneralhid " +
            "cioliabilityoid cioliabilityvid cliabilityoid cliabilityvid clocationid cmaterialoid cmaterialvid " +
            "corigcurrencyid corpoid corpvid coutcalbodyoid coutcalbodyvid cproductorid cprojectid cqualitylevelid " +
            "creceiveareaid creceivecustid creceivepointid crowno cselastunitid csignwastbid csignwasthid " +
            "csignwasttranstype csignwasttype csnqualitylevelid csnunitid csourcebillbid csourcebillhid " +
            "csourcetranstype csourcetype csourcewastbid csourcewasthid csourcewasttranstype csourcewasttype " +
            "csrc2billbid csrc2billhid csrc2billtype csrc2transtype csrcmaterialoid csrcmaterialvid cstateid csumid " +
            "cunitid cvendorid cvmivenderid dbizdate ddeliverdate dfirstbilldate dinbounddate dplanarrivedate " +
            "dplanoutdate dproducedate dr dvalidate flargess ibcversion idesatype naccumoutbacknum naccumoutsignnum " +
            "naccumvminum naccumwastnum nassistnum nbarcodenum ncorrespondastnum ncorrespondgrsnum ncorrespondnum " +
            "ncostmny ncostprice ncountnum ngrossnum nnum norigmny norignetprice norigtaxmny norigtaxnetprice npiece " +
            "nplannedmny nplannedprice nshouldassistnum nshouldnum ntarenum ntotaltrannum ntranoutastnum ntranoutnum " +
            "ntransinnum nvolume nweight pk_batchcode pk_group pk_measware pk_org pk_org_v pk_packsort pk_serialcode " +
            "tchecktime ts tsourcebodyts tsourceheadts vbatchcode vbatchcodenote vbcdef1 vbcdef10 vbcdef11 vbcdef12 " +
            "vbcdef13 vbcdef14 vbcdef15 vbcdef16 vbcdef17 vbcdef18 vbcdef19 vbcdef2 vbcdef20 vbcdef3 vbcdef4 vbcdef5 " +
            "vbcdef6 vbcdef7 vbcdef8 vbcdef9 vbdef1 vbdef10 vbdef11 vbdef12 vbdef13 vbdef14 vbdef15 vbdef16 vbdef17 " +
            "vbdef18 vbdef19 vbdef2 vbdef20 vbdef3 vbdef4 vbdef5 vbdef6 vbdef7 vbdef8 vbdef9 vbillbarcode " +
            "vbilltypeu8rm vchangerate vexigencybid vexigencycode vexigencyhid vexigencyrowno vexigencytype " +
            "vfirstbillcode vfirstrowno vfree1 vfree10 vfree2 vfree3 vfree4 vfree5 vfree6 vfree7 vfree8 vfree9 " +
            "vnotebody vreceiveaddress vserialcode vsignwastcode vsignwastrowno vsndef1 vsndef10 vsndef11 vsndef12 " +
            "vsndef13 vsndef14 vsndef15 vsndef16 vsndef17 vsndef18 vsndef19 vsndef2 vsndef20 vsndef21 vsndef22 " +
            "vsndef23 vsndef24 vsndef25 vsndef26 vsndef27 vsndef28 vsndef29 vsndef3 vsndef30 vsndef31 vsndef32 " +
            "vsndef33 vsndef34 vsndef35 vsndef36 vsndef37 vsndef38 vsndef39 vsndef4 vsndef40 vsndef5 vsndef6 vsndef7 " +
            "vsndef8 vsndef9 vsourcebillcode vsourcerowno vsourcewastcode vsourcewastrowno vsrc2billcode " +
            "vsrc2billrowno vtransfercode vvehiclecode vvendbatchcode");

    private static final Set<String> IC_TRANSOUT_L = fields(
            "cgeneralbid cgeneralhid cgenerallid clocationid corpoid corpvid csnqualitylevelid csnunitid dr " +
            "nassistnum ngrossnum nnum nshouldassistnum nshouldnum pk_group pk_serialcode ts vbarcode vbarcodesub " +
            "vboxbarcode vserialcode vsndef1 vsndef10 vsndef11 vsndef12 vsndef13 vsndef14 vsndef15 vsndef16 vsndef17 " +
            "vsndef18 vsndef19 vsndef2 vsndef20 vsndef21 vsndef22 vsndef23 vsndef24 vsndef25 vsndef26 vsndef27 " +
            "vsndef28 vsndef29 vsndef3 vsndef30 vsndef31 vsndef32 vsndef33 vsndef34 vsndef35 vsndef36 vsndef37 " +
            "vsndef38 vsndef39 vsndef4 vsndef40 vsndef5 vsndef6 vsndef7 vsndef8 vsndef9");

    // Local NC m4455 BMF fields; relationship and lifecycle columns require site schema verification.
    private static final Set<String> IC_SAPPLY_H = fields(
            "approver billmaker cbizid cconstructvendorid ccustomerid cdptid cdptvid cgeneralhid cissueorg " +
            "cissueorg_v corpoid corpvid creationtime creator ctrantypeid dbilldate dmakedate dr fbillflag " +
            "iprintcount modifiedtime modifier ntotalnum ntotalpiece ntotalvolume ntotalweight pk_group pk_org " +
            "pk_org_v taudittime ts vbillcode vdef1 vdef10 vdef11 vdef12 vdef13 vdef14 vdef15 vdef16 vdef17 vdef18 " +
            "vdef19 vdef2 vdef20 vdef3 vdef4 vdef5 vdef6 vdef7 vdef8 vdef9 vnote vtrantypecode");

    private static final Set<String> IC_SAPPLY_B = fields(
            "casscustid castunitid ccostobjectid cffileid cfirstbillbid cfirstbillhid cfirsttranstype cfirsttype " +
            "cgeneralbid cgeneralhid closed cmaterialoid cmaterialvid cmffileid corpoid corpvid costclsidreason " +
            "cprodasscustid cprodproductorid cprodprojectid cproductorid cprodvendorid cprojectcostclsid cprojectid " +
            "cprojecttaskid cqualitylevelid crcid crowno csourcebillbid csourcebillhid csourcetranstype csourcetype " +
            "cunitid cvendorid cwarehouseid cwastewarehouseid cworkcenterid cwp dinbounddate dplanrecvdate dr " +
            "ibcversion isclosed nassistnum nnum noriginaloutboundnum noutboundastnum noutboundnum noutboundrsnum " +
            "originalnum pk_batchcode pk_cbsnode pk_group pk_org pk_org_v ts tsourcebodyts tsourceheadts vbatchcode " +
            "vbcdef1 vbcdef10 vbcdef11 vbcdef12 vbcdef13 vbcdef14 vbcdef15 vbcdef16 vbcdef17 vbcdef18 vbcdef19 " +
            "vbcdef2 vbcdef20 vbcdef3 vbcdef4 vbcdef5 vbcdef6 vbcdef7 vbcdef8 vbcdef9 vbdef1 vbdef10 vbdef11 vbdef12 " +
            "vbdef13 vbdef14 vbdef15 vbdef16 vbdef17 vbdef18 vbdef19 vbdef2 vbdef20 vbdef3 vbdef4 vbdef5 vbdef6 " +
            "vbdef7 vbdef8 vbdef9 vchangerate vcontractcode vfirstbillcode vfirstrowno vfree1 vfree10 vfree2 vfree3 " +
            "vfree4 vfree5 vfree6 vfree7 vfree8 vfree9 vnotebody vprodfree1 vprodfree10 vprodfree2 vprodfree3 " +
            "vprodfree4 vprodfree5 vprodfree6 vprodfree7 vprodfree8 vprodfree9 vproductbatch vsourcebillcode " +
            "vsourcerowno");

    // NC m4455 dynamic body fields reside in this one-to-one table in QDNC.
    private static final Set<String> IC_SAPPLY_E = fields(
            "cgeneralbid dr noutboundastnum noutboundnum noutboundrsnum ts");

    static Set<String> forTable(String table) {
        if ("ic_purchasein_h".equals(table)) return IC_PURCHASEIN_H;
        if ("ic_purchasein_b".equals(table)) return IC_PURCHASEIN_B;
        if ("ic_purchasein_l".equals(table)) return IC_PURCHASEIN_L;
        if ("ic_saleout_h".equals(table)) return IC_SALEOUT_H;
        if ("ic_saleout_b".equals(table)) return IC_SALEOUT_B;
        if ("ic_saleout_l".equals(table)) return IC_SALEOUT_L;
        if ("ic_transin_h".equals(table)) return IC_TRANSIN_H;
        if ("ic_transin_b".equals(table)) return IC_TRANSIN_B;
        if ("ic_transin_l".equals(table)) return IC_TRANSIN_L;
        if ("ic_openbal_h".equals(table)) return IC_OPENBAL_H;
        if ("ic_openbal_b".equals(table)) return IC_OPENBAL_B;
        if ("ic_openbal_l".equals(table)) return IC_OPENBAL_L;
        if ("ic_locadjust_h".equals(table)) return IC_LOCADJUST_H;
        if ("ic_locadjust_b".equals(table)) return IC_LOCADJUST_B;
        if ("ic_locadjust_l".equals(table)) return IC_LOCADJUST_L;
        if ("ic_generalin_h".equals(table)) return IC_GENERALIN_H;
        if ("ic_generalin_b".equals(table)) return IC_GENERALIN_B;
        if ("ic_generalin_l".equals(table)) return IC_GENERALIN_L;
        if ("ic_transout_h".equals(table)) return IC_TRANSOUT_H;
        if ("ic_transout_b".equals(table)) return IC_TRANSOUT_B;
        if ("ic_transout_l".equals(table)) return IC_TRANSOUT_L;
        if ("ic_sapply_h".equals(table)) return IC_SAPPLY_H;
        if ("ic_sapply_b".equals(table)) return IC_SAPPLY_B;
        if ("ic_sapply_e".equals(table)) return IC_SAPPLY_E;
        if ("ic_material_h".equals(table)) return IC_MATERIAL_H;
        if ("ic_material_b".equals(table)) return IC_MATERIAL_B;
        if ("ic_material_l".equals(table)) return IC_MATERIAL_L;
        if ("ic_generalout_h".equals(table)) return IC_GENERALOUT_H;
        if ("ic_generalout_b".equals(table)) return IC_GENERALOUT_B;
        if ("ic_generalout_l".equals(table)) return IC_GENERALOUT_L;
        if ("ic_subcontin_h".equals(table)) return IC_SUBCONTIN_H;
        if ("ic_subcontin_b".equals(table)) return IC_SUBCONTIN_B;
        if ("ic_subcontin_l".equals(table)) return IC_SUBCONTIN_L;
        if ("ic_finprodin_h".equals(table)) return IC_FINPRODIN_H;
        if ("ic_finprodin_b".equals(table)) return IC_FINPRODIN_B;
        if ("ic_finprodin_l".equals(table)) return IC_FINPRODIN_L;
        if ("scm_batchcode".equals(table)) return SCM_BATCHCODE;
        throw new IllegalArgumentException("Unsupported inventory audit table: " + table);
    }

    private static Set<String> fields(String names) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(names.split(" "))));
    }
}
