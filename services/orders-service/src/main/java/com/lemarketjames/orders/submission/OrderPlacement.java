package com.lemarketjames.orders.submission;

import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.entity.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks an order and, if it passes, saves it, in one transaction (BR-05). The checks lock the
 * account ({@link SubmissionValidator}), and the lock is held until the order is saved, so the next
 * order on the account sees this one among its open orders.
 *
 * <p>A refusal saves nothing here: the transaction only releases the lock. The refusal's trail is
 * recorded afterwards, in a transaction of its own ({@link SubmissionRecorder#refuse}), so a check
 * that throws can't roll its trail back.
 */
@Service
public class OrderPlacement {

    private final SubmissionValidator validator;
    private final SubmissionRecorder submissions;

    public OrderPlacement(SubmissionValidator validator, SubmissionRecorder submissions) {
        this.validator = validator;
        this.submissions = submissions;
    }

    /** The outcome of one placement: the saved order, or the refusal to report. */
    public record Result(Order saved, ValidationOutcome refusal) {
        public boolean isRefused() {
            return refusal != null;
        }
    }

    /**
     * Runs the checks under the account lock and saves the order if they pass.
     *
     * @param trail receives the result of every check that runs
     * @throws RuntimeException whatever a check or the save throws (see {@link SubmissionValidator#validate}),
     *         after rolling back; {@link SubmissionTrail#hasPassedAll()} tells which
     */
    @Transactional
    public Result place(CreateOrderRequest request, SubmissionTrail trail) {
        ValidationOutcome outcome = validator.validate(request, trail);
        if (outcome.isRefused()) {
            return new Result(null, outcome);
        }
        trail.passedAll();

        // Persist only after all submission checks pass, for the shares the checks settled on.
        Order order = new Order(
            request.getAccountId(),
            request.getInstrumentId(),
            request.getOrderType(),
            outcome.quantity()
        );
        order.setRequestedAmount(request.getAmount());

        if (outcome.price() != null) {
            order.setPricePerUnit(outcome.price());
        }

        return new Result(submissions.accept(order, trail), null);
    }
}
