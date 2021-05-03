package org.gotson.komga.application.tasks

import mu.KotlinLogging
import org.gotson.komga.domain.persistence.BookRepository
import org.gotson.komga.domain.persistence.LibraryRepository
import org.gotson.komga.domain.persistence.SeriesRepository
import org.gotson.komga.domain.service.BookImporter
import org.gotson.komga.domain.service.BookLifecycle
import org.gotson.komga.domain.service.LibraryContentLifecycle
import org.gotson.komga.domain.service.MetadataLifecycle
import org.gotson.komga.infrastructure.jms.QUEUE_TASKS
import org.gotson.komga.infrastructure.jms.QUEUE_TASKS_SELECTOR
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import java.nio.file.Paths
import kotlin.time.measureTime

private val logger = KotlinLogging.logger {}

@Service
class TaskHandler(
  private val taskReceiver: TaskReceiver,
  private val libraryRepository: LibraryRepository,
  private val bookRepository: BookRepository,
  private val seriesRepository: SeriesRepository,
  private val libraryContentLifecycle: LibraryContentLifecycle,
  private val bookLifecycle: BookLifecycle,
  private val metadataLifecycle: MetadataLifecycle,
  private val bookImporter: BookImporter,
) {

  @JmsListener(destination = QUEUE_TASKS, selector = QUEUE_TASKS_SELECTOR)
  fun handleTask(task: Task) {
    logger.info { "Executing task: $task" }
    try {
      measureTime {
        when (task) {

          is Task.ScanLibrary ->
            libraryRepository.findByIdOrNull(task.libraryId)?.let {
              libraryContentLifecycle.scanRootFolder(it)
              taskReceiver.analyzeUnknownAndOutdatedBooks(it)
            } ?: logger.warn { "Cannot execute task $task: Library does not exist" }

          is Task.AnalyzeBook ->
            bookRepository.findByIdOrNull(task.bookId)?.let {
              if (bookLifecycle.analyzeAndPersist(it)) {
                taskReceiver.generateBookThumbnail(it.id, priority = task.priority + 1)
                taskReceiver.refreshBookMetadata(it, priority = task.priority + 1)
              }
            } ?: logger.warn { "Cannot execute task $task: Book does not exist" }

          is Task.GenerateBookThumbnail ->
            bookRepository.findByIdOrNull(task.bookId)?.let {
              bookLifecycle.generateThumbnailAndPersist(it)
            } ?: logger.warn { "Cannot execute task $task: Book does not exist" }

          is Task.RefreshBookMetadata ->
            bookRepository.findByIdOrNull(task.bookId)?.let {
              metadataLifecycle.refreshMetadata(it, task.capabilities)
              taskReceiver.refreshSeriesMetadata(it.seriesId)
            } ?: logger.warn { "Cannot execute task $task: Book does not exist" }

          is Task.RefreshSeriesMetadata ->
            seriesRepository.findByIdOrNull(task.seriesId)?.let {
              metadataLifecycle.refreshMetadata(it)
              taskReceiver.aggregateSeriesMetadata(it.id)
            } ?: logger.warn { "Cannot execute task $task: Series does not exist" }

          is Task.AggregateSeriesMetadata ->
            seriesRepository.findByIdOrNull(task.seriesId)?.let {
              metadataLifecycle.aggregateMetadata(it)
            } ?: logger.warn { "Cannot execute task $task: Series does not exist" }

          is Task.ImportBook ->
            seriesRepository.findByIdOrNull(task.seriesId)?.let { series ->
              bookImporter.importBook(Paths.get(task.sourceFile), series, task.copyMode, task.destinationName, task.upgradeBookId)
            } ?: logger.warn { "Cannot execute task $task: Series does not exist" }
        }
      }.also {
        logger.info { "Task $task executed in $it" }
      }
    } catch (e: Exception) {
      logger.error(e) { "Task $task execution failed" }
    }
  }
}
