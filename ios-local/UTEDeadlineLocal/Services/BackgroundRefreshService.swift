import BackgroundTasks
import Foundation

final class BackgroundRefreshService {
    static let shared = BackgroundRefreshService()

    static let taskIdentifier = "com.utex.deadline.refresh"

    private init() {}

    func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.taskIdentifier, using: nil) { task in
            guard let task = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            self.handle(task)
        }
    }

    func schedule() {
        guard !EventStore.shared.iCalURL.isEmpty else {
            BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.taskIdentifier)
            return
        }

        let request = BGAppRefreshTaskRequest(identifier: Self.taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 30 * 60)

        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            // iOS may reject duplicate or currently unavailable requests. The next foreground sync will try again.
        }
    }

    private func handle(_ task: BGAppRefreshTask) {
        schedule()

        let syncTask = Task {
            let result = await DeadlineSyncService.shared.sync(notifyNew: true)
            task.setTaskCompleted(success: result.ok || !result.retryable)
        }

        task.expirationHandler = {
            syncTask.cancel()
        }
    }
}
