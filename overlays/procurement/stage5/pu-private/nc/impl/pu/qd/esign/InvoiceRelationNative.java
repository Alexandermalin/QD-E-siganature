package nc.impl.pu.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.sql.Types;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.pub.filesystem.IFileSystemService;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.ColumnProcessor;
import nc.pu.m25.util.QueryDefInfo;
import nc.vo.pu.m25.entity.InvoiceHeaderVO;
import nc.vo.pu.m25.entity.InvoiceItemVO;
import nc.vo.pu.qd.esign.InvoiceRelationCommand;
import nc.vo.pu.qd.esign.InvoiceRelationCommand.Amounts;
import nc.vo.pu.qd.esign.InvoiceRelationCommand.Target;
import nc.vo.pub.BusinessException;
import nc.vo.pub.filesystem.NCFileVO;
import nc.vo.pub.lang.UFDouble;
import nc.vo.uapbd.qd.esign.OperationContext;
import org.json.JSONArray;
import org.json.JSONObject;

import static nc.vo.pu.qd.esign.InvoiceRelationCommand.safeKey;
import static nc.vo.pu.qd.esign.InvoiceRelationCommand.text;

/** Builds all NC changes before sending the external request. SQL matches the installed 齐都 business fields. */
final class InvoiceRelationNative {
    private final BaseDAO dao;
    private final InvoiceHeaderVO head;
    private final InvoiceRelationCommand command;
    private final String state;
    private final UFDouble gross;
    private final List<LineChange> lines;
    private final String[] numbers;
    final String externalNumbers;
    private final InvoiceRelationExternal remote;

    private InvoiceRelationNative(BaseDAO dao, InvoiceHeaderVO head, InvoiceRelationCommand command, String state,
            UFDouble gross, List<LineChange> lines, String[] numbers, InvoiceRelationExternal remote) {
        this.dao = dao;
        this.head = head;
        this.command = command;
        this.state = state;
        this.gross = gross;
        this.lines = lines;
        this.numbers = numbers;
        this.remote = remote;
        this.externalNumbers = command.isRelate() ? command.joined(false) : command.getPreviousNumbers();
    }

    static InvoiceRelationNative prepare(OperationContext scope, InvoiceRelationCommand command) throws BusinessException {
        BaseDAO dao = new BaseDAO();
        String pk = command.getInvoicePk();
        Object locked = dao.executeQuery("select pk_invoice from po_invoice where pk_invoice = ?"
                + " and (dr = 0 or dr is null) for update", params(pk), new ColumnProcessor());
        Collection<?> headers = dao.retrieveByClause(InvoiceHeaderVO.class, "pk_invoice = ? and (dr = 0 or dr is null)",
                params(pk));
        if (!pk.equals(locked) || headers.size() != 1) throw new BusinessException("找不到已授权的采购发票。");
        InvoiceHeaderVO head = (InvoiceHeaderVO) headers.iterator().next();
        if (!scope.getGroupId().equals(head.getPk_group()) || !scope.getOrgId().equals(head.getPk_org())
                || !Objects.equals(scope.getObjects().get(0).getBillCode(), head.getVbillcode())
                || head.getTs() == null || !command.getTs().equals(head.getTs().toString())
                || !command.getPreviousNumbers().equals(text(head.getVdef1()))
                || !command.getPreviousIds().equals(text(head.getVdef3()))
                || !command.getPreviousState().equals(text(head.getVdef2()))) {
            throw new BusinessException("采购发票信息已经变化，请刷新后重试。");
        }
        dao.executeQuery("select pk_invoice_b from po_invoice_b where pk_invoice = ? and (dr = 0 or dr is null)"
                + " order by pk_invoice_b for update", params(pk), new nc.jdbc.framework.processor.ColumnListProcessor());
        Collection<?> items = dao.retrieveByClause(InvoiceItemVO.class, "pk_invoice = ? and (dr = 0 or dr is null)",
                params(pk));
        InvoiceItemVO[] rows = items.toArray(new InvoiceItemVO[items.size()]);
        Amounts current = InvoiceRelationCommand.amounts(head, rows);
        if (!command.getOriginal().same(current)) throw new BusinessException("采购发票金额已经变化，请刷新后重试。");

        Map<String, String> states = QueryDefInfo.getInstance().getDefInfo();
        String linked = states == null ? null : states.get("已关联");
        String clear = states == null ? null : states.get("未关联");
        if (!safeKey(linked, 20) || !safeKey(clear, 20) || linked.equals(clear) || head.getFbillstatus() == null) {
            throw new BusinessException("采购发票关联状态配置不完整。");
        }

        String[] numbers;
        UFDouble gross;
        List<LineChange> lines = new ArrayList<LineChange>();
        int status = head.getFbillstatus().intValue();
        if (command.isRelate()) {
            if ((status != 0 && status != 4) || linked.equals(head.getVdef2())
                    || !command.getPreviousNumbers().isEmpty() || !command.getPreviousIds().isEmpty()) {
                throw new BusinessException("采购发票当前状态不允许再次关联。");
            }
            numbers = command.joined(false).split(";", -1);
            Amounts selected = command.selectedAmounts();
            gross = selected.gross;
            if (!current.same(selected)) {
                UFDouble grossDiff = current.gross.sub(selected.gross);
                UFDouble taxDiff = current.tax.sub(selected.tax);
                for (InvoiceItemVO item : rows) if ("10".equals(item.getCrowno())) {
                    UFDouble tax = zero(item.getNtax()).sub(taxDiff);
                    UFDouble newGross = item.getNorigtaxmny() == null ? null : item.getNorigtaxmny().sub(grossDiff);
                    if (newGross == null) throw new BusinessException("采购发票第一行缺少价税合计。");
                    lines.add(new LineChange(item.getPk_invoice_b(), newGross.sub(tax), tax, newGross));
                }
            }
        } else {
            if (status != 0 || clear.equals(head.getVdef2())) {
                throw new BusinessException("采购发票当前状态不允许取消关联。");
            }
            numbers = command.getPreviousNumbers().isEmpty() ? new String[0] : command.getPreviousNumbers().split(";", -1);
            gross = current.gross;
            if (numbers.length != 0) {
                for (InvoiceItemVO item : rows) {
                    lines.add(new LineChange(item.getPk_invoice_b(), restore(item.getVbdef1(), item.getNorigmny()),
                            restore(item.getVbdef2(), item.getNtax()), restore(item.getVbdef3(), item.getNorigtaxmny())));
                }
            }
        }
        if (new HashSet<String>(Arrays.asList(numbers)).size() != numbers.length) {
            throw new BusinessException("采购发票存在重复数电发票号码。");
        }
        for (String number : numbers) if (!safeKey(number, 101)) throw new BusinessException("数电发票号码无效。");
        amount(gross);
        InvoiceRelationExternal remote = numbers.length == 0 ? null
                : new InvoiceRelationExternal(payload(command, head, numbers), numbers);
        return new InvoiceRelationNative(dao, head, command, command.isRelate() ? linked : clear, gross, lines, numbers,
                remote);
    }

    private static String payload(InvoiceRelationCommand command, InvoiceHeaderVO head, String[] numbers)
            throws BusinessException {
        try {
            JSONArray list = new JSONArray();
            for (int i = 0; i < numbers.length; i++) {
                JSONObject row = new JSONObject();
                String id = command.isRelate() ? command.getTargets().get(i).id : head.getVdef3();
                if (id != null) row.put("id", id);
                row.put("fphm", numbers[i]);
                row.put("ncglzt", command.isRelate() ? "1" : "0");
                row.put("jzje", head.getNtotalorigmny().toString());
                row.put("jzdh", head.getVbillcode());
                list.put(row);
            }
            return new JSONObject().put("invoices", list).toString();
        } catch (Exception ex) {
            throw new BusinessException("无法生成数电发票回传数据。");
        }
    }

    String external() throws Exception {
        return remote.call();
    }

    void local() throws BusinessException {
        String pk = head.getPk_invoice();
        String ids = command.isRelate() ? command.joined(true) : null;
        String linked = command.isRelate() ? command.joined(false) : null;
        if (dao.executeUpdate("update po_invoice set vdef1 = ?, vdef2 = ?, vdef3 = ?, ntotalorigmny = ?"
                + " where pk_invoice = ? and (dr = 0 or dr is null)", headerParams(linked, state, ids, gross, pk)) != 1) {
            throw new BusinessException("采购发票表头未按原关联结果更新。");
        }
        for (LineChange line : lines) {
            if (dao.executeUpdate("update po_invoice_b set norigmny = ?, ntax = ?, norigtaxmny = ?"
                    + " where pk_invoice = ? and pk_invoice_b = ? and (dr = 0 or dr is null)",
                    lineParams(line, pk)) != 1) {
                throw new BusinessException("采购发票明细未按原关联结果更新。");
            }
        }
        if (numbers.length == 0) return;
        IFileSystemService files = NCLocator.getInstance().lookup(IFileSystemService.class);
        if (command.isRelate()) {
            for (Target target : command.getTargets()) {
                if (target.pdfUrl == null || target.pdfUrl.isEmpty()) continue;
                NCFileVO attachment = new NCFileVO();
                attachment.setPath(pk + "/" + target.number);
                attachment.setCreator(InvocationInfoProxy.getInstance().getUserId());
                attachment.setFiletype("url");
                attachment.setFileLen(0);
                attachment.setPk_doc(target.pdfUrl);
                attachment.setTime(System.currentTimeMillis() / 1000L);
                if (!files.insertURLFileVO(attachment)) throw new BusinessException("数电发票附件未能挂载。");
            }
        } else {
            String[] paths = new String[numbers.length];
            for (int i = 0; i < numbers.length; i++) paths[i] = pk + "/" + numbers[i];
            files.deleteNCFileNodes(paths);
            for (String path : paths) if (files.isExist(path)) throw new BusinessException("数电发票附件未能删除。");
        }
    }

    private static UFDouble restore(String original, UFDouble current) throws BusinessException {
        if (text(original).isEmpty()) return current;
        try {
            return new UFDouble(original);
        } catch (NumberFormatException ex) {
            throw new BusinessException("采购发票明细的原金额无法恢复。");
        }
    }

    private static UFDouble zero(UFDouble value) { return value == null ? UFDouble.ZERO_DBL : value; }

    private static void amount(UFDouble value) throws BusinessException {
        if (value != null) {
            java.math.BigDecimal decimal = value.toBigDecimal().stripTrailingZeros();
            if (decimal.scale() > 8 || decimal.precision() - decimal.scale() > 20) {
                throw new BusinessException("采购发票金额超出字段精度。");
            }
        }
    }

    private static SQLParameter params(String key) {
        SQLParameter p = new SQLParameter();
        p.addParam(key);
        return p;
    }

    static SQLParameter headerParams(String numbers, String state, String ids, UFDouble gross, String pk) {
        SQLParameter p = new SQLParameter();
        if (numbers == null) p.addNullParam(Types.VARCHAR); else p.addParam(numbers);
        p.addParam(state);
        if (ids == null) p.addNullParam(Types.VARCHAR); else p.addParam(ids);
        p.addParam(gross);
        p.addParam(pk);
        return p;
    }

    private static SQLParameter lineParams(LineChange line, String pk) {
        SQLParameter p = new SQLParameter();
        for (UFDouble value : new UFDouble[] {line.net, line.tax, line.gross}) {
            if (value == null) p.addNullParam(Types.DECIMAL); else p.addParam(value);
        }
        p.addParam(pk);
        p.addParam(line.pk);
        return p;
    }

    private static final class LineChange {
        final String pk;
        final UFDouble net;
        final UFDouble tax;
        final UFDouble gross;

        LineChange(String pk, UFDouble net, UFDouble tax, UFDouble gross) throws BusinessException {
            if (!safeKey(pk, 20)) throw new BusinessException("采购发票明细主键无效。");
            amount(net);
            amount(tax);
            amount(gross);
            this.pk = pk;
            this.net = net;
            this.tax = tax;
            this.gross = gross;
        }
    }
}
