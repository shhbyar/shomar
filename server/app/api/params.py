from datetime import datetime
from typing import Annotated

from fastapi import Query

UpdatedSince = Annotated[
    datetime | None,
    Query(
        description=(
            "Sync mode: return rows changed after this instant (ISO 8601; UTC if no "
            "offset is given), including soft-deleted ones. Without it, deleted rows are hidden."
        )
    ),
]
