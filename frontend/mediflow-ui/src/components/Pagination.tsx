export interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  pageSize: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, totalPages, totalElements, pageSize, onPageChange }: PaginationProps) {
  if (totalPages <= 1) {
    return totalElements > 0 ? (
      <p className="pagination-summary">
        {totalElements} item{totalElements === 1 ? '' : 's'}
      </p>
    ) : null;
  }

  const from = page * pageSize + 1;
  const to = Math.min((page + 1) * pageSize, totalElements);

  return (
    <div className="pagination">
      <p className="pagination-summary">
        {from}–{to} of {totalElements}
      </p>
      <div className="pagination-buttons">
        <button
          type="button"
          className="btn secondary-btn"
          disabled={page === 0}
          onClick={() => onPageChange(page - 1)}
        >
          Previous
        </button>
        <span className="pagination-page">
          Page {page + 1} of {totalPages}
        </span>
        <button
          type="button"
          className="btn secondary-btn"
          disabled={page >= totalPages - 1}
          onClick={() => onPageChange(page + 1)}
        >
          Next
        </button>
      </div>
    </div>
  );
}