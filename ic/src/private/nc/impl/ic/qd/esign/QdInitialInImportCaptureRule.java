package nc.impl.ic.qd.esign;

import nc.impl.pubapp.pattern.rule.IRule;
import nc.vo.ic.m40.entity.InitializtionInVO;

/** Candidate hook; do not register until the authorized import and transaction path is verified. */
public final class QdInitialInImportCaptureRule implements IRule<InitializtionInVO> {
    public void process(InitializtionInVO[] bills) {
        InventoryInitialImportCapture.observe(bills);
    }
}
