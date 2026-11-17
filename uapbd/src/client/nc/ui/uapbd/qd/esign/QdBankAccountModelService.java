package nc.ui.uapbd.qd.esign;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import javax.swing.SwingUtilities;
import nc.ui.bd.bankacc.cust.model.CustBankaccAppModel;
import nc.ui.bd.bankacc.cust.model.CustBankaccAppModelService;
import nc.vo.bd.bankaccount.cust.CustBankaccUnionVO;
import nc.vo.pub.BusinessException;

/** Keeps the native save, save-add, close-save and delete actions on one controlled service. */
public final class QdBankAccountModelService extends CustBankaccAppModelService {
    private CustBankaccAppModel model;
    private boolean executing;

    public void setModel(CustBankaccAppModel value) { model=value; }

    @Override public Object insert(Object value) throws Exception { return execute(value,"INSERT"); }
    @Override public Object update(Object value) throws Exception { return execute(value,"UPDATE"); }
    @Override public void delete(Object value) throws Exception { execute(value,"DELETE"); }

    private CustBankaccUnionVO execute(Object value,final String operation) throws Exception {
        if(!(value instanceof CustBankaccUnionVO)) throw new BusinessException("银行账户操作数据不正确。");
        final CustBankaccUnionVO account=(CustBankaccUnionVO)value;
        if(SwingUtilities.isEventDispatchThread()) return executeOnEventThread(account,operation);
        // NC DeleteAction calls the model on a worker. The signature dialog belongs to the EDT.
        FutureTask<CustBankaccUnionVO> task=new FutureTask<CustBankaccUnionVO>(new Callable<CustBankaccUnionVO>() {
            public CustBankaccUnionVO call() throws Exception { return executeOnEventThread(account,operation); }
        });
        SwingUtilities.invokeLater(task);
        try { return task.get(); }
        catch(InterruptedException error) {
            task.cancel(false);
            Thread.currentThread().interrupt();
            throw new BusinessException("银行账户操作已中断，请刷新并核对结果。",error);
        } catch(ExecutionException error) {
            Throwable cause=error.getCause();
            if(cause instanceof Exception) throw (Exception)cause;
            if(cause instanceof Error) throw (Error)cause;
            throw new BusinessException("银行账户操作失败。",error);
        }
    }

    private CustBankaccUnionVO executeOnEventThread(CustBankaccUnionVO value,String operation) throws BusinessException {
        if(executing || model==null) throw new BusinessException("银行账户弹窗尚未就绪或正在处理。");
        executing=true;
        try { return new QdBankAccountCoordinator(model,operation,null,value).execute(); }
        finally { executing=false; }
    }
}
