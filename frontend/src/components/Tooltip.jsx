import { CircleAlert } from "lucide-react";

function Tooltip({ message }) {
  return (
    <div className="relative group flex items-center">
      <CircleAlert
        aria-label={message}
        size={15}
        strokeWidth={2.5}
        className="text-[#204867]"
      />
      <div className="absolute left-[17px] hidden group-hover:block w-0 h-0 border-t-[4px] border-t-transparent border-b-[4px] border-b-transparent border-r-[8px] border-[#204867]"></div>
      <div
        aria-hidden="true"
        className="absolute left-[24px] text-center hidden group-hover:block border-[#204867] bg-[#204867] text-[#FFFFFF] text-[10px] md:text-[12px] font-normal w-[210px] md:w-[249px] py-[4px] px-[6px] md:py-[4px] md:px-[8px] rounded-[4px]"
      >
        {message}
      </div>
    </div>
  );
}

export default Tooltip;
