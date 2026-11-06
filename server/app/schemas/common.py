from typing import Annotated

from pydantic import Field

Currency = Annotated[str, Field(pattern=r"^[A-Z]{3}$")]  # ISO 4217
# Keeps values comfortably inside BIGINT.
MinorUnits = Annotated[int, Field(ge=-(10**15), le=10**15)]
